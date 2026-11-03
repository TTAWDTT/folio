package cst3492.courseta;

import com.google.gson.JsonParser;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.NSWorkspace;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.cef.browser.*;
import org.cef.callback.*;
import org.cef.handler.*;
import org.cef.misc.*;
import org.cef.network.*;
import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import static cst3492.courseta.FolioView.*;

final class FolioBrowser implements Disposable {
    static final String CSP = "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";
    private record Document(String token, String frame) {}
    private final FolioSession session;
    private final JPanel component = new JPanel(new BorderLayout());
    private final String identity = UUID.randomUUID().toString();
    private final String origin = "https://folio-" + UUID.randomUUID() + ".invalid";
    private final String page = origin + "/index.html";
    private final String queryName = "folioQuery_" + UUID.randomUUID().toString().replace("-", "");
    private final String cancelName = queryName + "Cancel";
    private final Gate gate = new Gate(identity);
    private JBCefBrowser browser;
    private CefMessageRouter router;
    private volatile Document document;
    private volatile boolean disposed;
    private long hseq;
    private boolean ready;
    private Resources resources;
    private javax.swing.Timer themeTimer;
    private Theme lastTheme;

    FolioBrowser(FolioSession session) {
        FolioSession.requireEdt(); this.session = session;
        if (!JBCefApp.isSupported()) { explain("此 IDE 运行时不支持 JCEF。请使用包含 JCEF 的 JetBrains Runtime 打开 Folio。"); return; }
        try { resources = new Resources(origin, FolioBrowser.class.getClassLoader()); }
        catch (RuntimeException error) { explain("Folio 网页资源不完整，请重新安装完整的插件包。"); return; }
        browser = new JBCefBrowser();
        browser.setOpenLinksInExternalBrowser(false);
        router = CefMessageRouter.create(new CefMessageRouter.CefMessageRouterConfig(queryName, cancelName));
        router.addHandler(new CefMessageRouterHandlerAdapter() {
            @Override public boolean onQuery(CefBrowser cef, CefFrame frame, long queryId, String request, boolean persistent, CefQueryCallback callback) {
                Document captured = document;
                if (disposed || cef != browser.getCefBrowser() || frame == null || !frame.isMain() || !frame.isValid()
                        || !page.equals(frame.getURL()) || captured == null || !captured.frame().equals(frame.getIdentifier()) || persistent) {
                    callback.failure(1, "Untrusted Folio document."); return true;
                }
                if (request == null || request.length() > FolioCodec.MAX_CLIENT_CHARS) { callback.failure(2, "Invalid Folio message."); return true; }
                SwingUtilities.invokeLater(() -> receive(captured, request));
                callback.success("");
                return true;
            }
        }, true);
        browser.getJBCefClient().getCefClient().addMessageRouter(router);
        browser.getJBCefClient().addRequestHandler(new CefRequestHandlerAdapter() {
            @Override public boolean onBeforeBrowse(CefBrowser cef, CefFrame frame, CefRequest request, boolean userGesture, boolean redirect) {
                if (disposed || frame == null || !frame.isMain() || !page.equals(request.getURL()) || !"GET".equals(request.getMethod()) || redirect) return true;
                Document next = new Document(UUID.randomUUID().toString(), frame.getIdentifier()); document = next;
                SwingUtilities.invokeLater(() -> {
                    if (document != next || disposed) return;
                    session.revokeDocument(); ready = false; gate.newDocument(next.token()); hseq = 0;
                });
                return false;
            }
            @Override public boolean onOpenURLFromTab(CefBrowser cef, CefFrame frame, String target, boolean userGesture) { return true; }
            @Override public CefResourceRequestHandler getResourceRequestHandler(CefBrowser cef, CefFrame frame, CefRequest request, boolean navigation, boolean download, String initiator, BoolRef disableDefault) {
                disableDefault.set(true);
                return new CefResourceRequestHandlerAdapter() {
                    @Override public boolean onBeforeResourceLoad(CefBrowser cef, CefFrame frame, CefRequest request) {
                        return disposed || !"GET".equals(request.getMethod()) || !resources.allowed(request.getURL(), true);
                    }
                    @Override public CefResourceHandler getResourceHandler(CefBrowser cef, CefFrame frame, CefRequest request) {
                        if (disposed || !"GET".equals(request.getMethod())) return response(null, "text/plain");
                        String path = resources.path(request.getURL());
                        if ("boot.js".equals(path)) {
                            Document current = document;
                            if (frame == null || !frame.isMain() || current == null || !current.frame().equals(frame.getIdentifier())) return response(null, "text/plain");
                            return response(boot(current.token(), queryName).getBytes(StandardCharsets.UTF_8), "text/javascript");
                        }
                        byte[] bytes = resources.read(path); return response(bytes, Resources.mime(path));
                    }
                    @Override public void onProtocolExecution(CefBrowser cef, CefFrame frame, CefRequest request, BoolRef allow) { allow.set(false); }
                };
            }
            @Override public void onRenderProcessTerminated(CefBrowser cef, TerminationStatus status, int code, String message) {
                document = null; SwingUtilities.invokeLater(() -> { if (!disposed) { ready = false; session.revokeDocument(); } });
            }
        }, browser.getCefBrowser());
        browser.getJBCefClient().addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override public boolean onBeforePopup(CefBrowser cef, CefFrame frame, String url, String name) { return true; }
        }, browser.getCefBrowser());
        component.add(browser.getComponent(), BorderLayout.CENTER);
        ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(LafManagerListener.TOPIC, source -> updateTheme());
        themeTimer = new javax.swing.Timer(1000, event -> updateTheme()); themeTimer.start(); updateTheme();
        browser.loadURL(page);
    }
    JComponent component() { return component; }
    private void explain(String text) { JLabel label = new JLabel("<html><body style='width:220px'>" + text + "</body></html>"); label.setBorder(JBUI.Borders.empty(12)); component.add(label, BorderLayout.NORTH); }
    private void receive(Document captured, String request) {
        FolioSession.requireEdt(); if (disposed || captured != document) return;
        FolioCodec.Client message;
        try { message = FolioCodec.decode(request); } catch (IllegalArgumentException error) { return; }
        if (!ready && !message.ready()) return;
        Gate.Decision decision = gate.admit(message);
        if (decision == Gate.Decision.IGNORE) return;
        if (decision == Gate.Decision.READY) {
            ready = true;
            session.attach(change -> { if (document == captured && !disposed && ready) send(captured, change); }, gate.processed());
            return;
        }
        if (!ready) return;
        if (decision == Gate.Decision.DUPLICATE) { send(captured, new Change.Ack(message.cseq(), gate.lastResult())); return; }
        if (decision == Gate.Decision.GAP) { send(captured, new Change.Ack(message.cseq(), Result.reject("stale", "命令序号不连续，请重新同步。"))); return; }
        FolioSession.Lease lease = session.lease(() -> !disposed && document == captured);
        Result result = session.execute(message.command(), lease); gate.settled(result); send(captured, new Change.Ack(message.cseq(), result));
    }
    private void send(Document target, Change change) {
        if (disposed || document != target || browser == null) return;
        String json = FolioCodec.host(target.token(), identity, ++hseq, change);
        browser.getCefBrowser().executeJavaScript("window.dispatchEvent(new CustomEvent('folio:host',{detail:" + json + "}));", page, 1);
    }
    static String boot(String document, String queryName) {
        if (!document.matches("[a-zA-Z0-9-]+") || !queryName.matches("[a-zA-Z0-9_]+")) throw new IllegalArgumentException("Invalid boot identity.");
        return "(()=>{const query=window." + queryName + ";Object.defineProperty(window,'folioHost',{value:Object.freeze({document:'" + document
                + "',postMessage(json){if(typeof json==='string')query({request:json,persistent:false,onSuccess(){},onFailure(){}})}}),writable:false,configurable:false});})();";
    }
    private void updateTheme() {
        FolioSession.requireEdt(); if (disposed) return;
        var font = UIUtil.getLabelFont(); boolean reduced = SystemInfoRt.isMac && !GraphicsEnvironment.isHeadless()
                && Foundation.invoke(NSWorkspace.getInstance(), "accessibilityDisplayShouldReduceMotion").intValue() != 0;
        Theme next = new Theme(UIUtil.isUnderDarcula(), reduced, font.getFamily(), font.getSize(), JBUI.scale(100) / 100.0);
        if (!next.equals(lastTheme)) { lastTheme = next; session.theme(next); }
    }
    private static CefResourceHandler response(byte[] bytes, String mime) {
        return new CefResourceHandlerAdapter() {
            private int offset;
            @Override public boolean processRequest(CefRequest request, CefCallback callback) { callback.Continue(); return true; }
            @Override public void getResponseHeaders(CefResponse response, IntRef length, StringRef redirect) {
                response.setStatus(bytes == null ? 404 : 200); response.setStatusText(bytes == null ? "Not Found" : "OK"); response.setMimeType(mime);
                response.setHeaderMap(Map.of("Content-Security-Policy", CSP, "Cache-Control", "no-store", "X-Content-Type-Options", "nosniff", "Referrer-Policy", "no-referrer")); length.set(bytes == null ? 0 : bytes.length);
            }
            @Override public boolean readResponse(byte[] destination, int requested, IntRef read, CefCallback callback) {
                if (bytes == null || offset >= bytes.length) { read.set(0); return false; }
                int count = Math.min(requested, bytes.length - offset); System.arraycopy(bytes, offset, destination, 0, count); offset += count; read.set(count); return true;
            }
        };
    }
    @Override public void dispose() {
        FolioSession.requireEdt(); if (disposed) return; disposed = true; document = null; ready = false; session.revokeDocument();
        if (themeTimer != null) themeTimer.stop();
        if (browser != null) { browser.getJBCefClient().getCefClient().removeMessageRouter(router); router.dispose(); browser.dispose(); }
        session.dispose();
    }

    static final class Gate {
        enum Decision { READY, COMMAND, DUPLICATE, GAP, IGNORE }
        private final String session;
        private String document;
        private long processed;
        private Result lastResult = Result.ok();
        Gate(String session) { this.session = session; }
        void newDocument(String document) { this.document = document; processed = 0; lastResult = Result.ok(); }
        long processed() { return processed; }
        Result lastResult() { return lastResult; }
        void settled(Result result) { lastResult = result; }
        Decision admit(FolioCodec.Client message) {
            if (!Objects.equals(document, message.document())) return Decision.IGNORE;
            if (message.ready()) return Decision.READY;
            if (!session.equals(message.session())) return Decision.IGNORE;
            if (message.cseq() == processed) return Decision.DUPLICATE;
            if (message.cseq() < processed) return Decision.GAP;
            if (message.cseq() != processed + 1) return Decision.GAP;
            processed = message.cseq(); return Decision.COMMAND;
        }
    }
    static final class Resources {
        private final String origin;
        private final Map<String, byte[]> files;
        Resources(String origin, ClassLoader loader) {
            this.origin = origin;
            try (var input = loader.getResourceAsStream("folio-web/asset-manifest.json")) {
                if (input == null) throw new IllegalArgumentException("Missing asset manifest.");
                Set<String> paths = manifest(new String(input.readNBytes(1_048_577), StandardCharsets.UTF_8));
                Map<String, byte[]> content = new LinkedHashMap<>();
                for (String path : paths) try (var asset = loader.getResourceAsStream("folio-web/" + path)) {
                    if (asset == null) throw new IllegalArgumentException("Missing asset."); byte[] bytes = asset.readNBytes(16_777_217);
                    if (bytes.length > 16_777_216) throw new IllegalArgumentException("Oversized asset."); content.put(path, bytes);
                }
                files = Map.copyOf(content);
            } catch (java.io.IOException error) { throw new IllegalArgumentException("Cannot read Folio assets."); }
        }
        Resources(String origin, Map<String, byte[]> files) { this.origin = origin; this.files = Map.copyOf(files); }
        static Set<String> manifest(String json) {
            if (json.length() > 1_048_576) throw new IllegalArgumentException("Oversized asset manifest.");
            var array = JsonParser.parseString(json).getAsJsonArray(); Set<String> result = new LinkedHashSet<>();
            for (var entry : array) {
                if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Invalid asset manifest.");
                String path = entry.getAsString();
                if (!(path.equals("index.html") || path.matches("assets/[a-zA-Z0-9_./-]+")) || path.contains("..") || path.contains("//") || !result.add(path)) throw new IllegalArgumentException("Invalid asset path.");
            }
            if (!result.contains("index.html")) throw new IllegalArgumentException("Missing entry page."); return Set.copyOf(result);
        }
        String path(String url) {
            try {
                URI uri = URI.create(url); String path = uri.getRawPath();
                if (!origin.equals(uri.getScheme() + "://" + uri.getRawAuthority()) || uri.getRawQuery() != null || uri.getRawFragment() != null || path == null || !path.startsWith("/") || path.indexOf('%') >= 0 || path.indexOf('\\') >= 0) return null;
                return path.substring(1);
            } catch (RuntimeException error) { return null; }
        }
        boolean allowed(String url, boolean boot) { String path = path(url); return path != null && (files.containsKey(path) || boot && path.equals("boot.js")); }
        byte[] read(String path) { return path == null ? null : files.get(path); }
        static String mime(String path) {
            if (path == null) return "text/plain";
            if (path.endsWith(".html")) return "text/html"; if (path.endsWith(".js")) return "text/javascript"; if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".svg")) return "image/svg+xml"; if (path.endsWith(".png")) return "image/png"; if (path.endsWith(".woff2")) return "font/woff2"; return "application/octet-stream";
        }
    }
}
