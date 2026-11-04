package cst3492.courseta;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.StringReader;
import java.util.*;
import static cst3492.courseta.FolioView.*;

final class FolioCodec {
    static final int MAX_CLIENT_CHARS = 1_048_576;
    record Client(String document, String session, long cseq, FolioCommand command) {
        boolean ready() { return command == null; }
        @Override public String toString() { return "Client[redacted]"; }
    }
    static Client decode(String text) {
        if (text == null || text.length() > MAX_CLIENT_CHARS) throw invalid();
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            JsonObject root = read(reader, 0).getAsJsonObject();
            if (reader.peek() != JsonToken.END_DOCUMENT || number(root, "v") != 1) throw invalid();
            String kind = string(root, "kind", 32);
            String document = string(root, "document", 128);
            if (kind.equals("ready")) {
                fields(root, "v", "document", "kind");
                return new Client(document, null, 0, null);
            }
            if (!kind.equals("command")) throw invalid();
            fields(root, "v", "document", "session", "cseq", "kind", "command");
            long sequence = number(root, "cseq");
            if (sequence < 1) throw invalid();
            return new Client(document, string(root, "session", 128), sequence, command(object(root, "command")));
        } catch (Exception error) { throw invalid(); }
    }
    private static JsonElement read(JsonReader reader, int depth) throws java.io.IOException {
        if (depth > 16) throw invalid();
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject(); JsonObject object = new JsonObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name) || object.size() > 32) throw invalid();
                    object.add(name, read(reader, depth + 1));
                }
                reader.endObject(); yield object;
            }
            case BEGIN_ARRAY -> {
                reader.beginArray(); JsonArray array = new JsonArray();
                while (reader.hasNext()) {
                    array.add(read(reader, depth + 1));
                }
                reader.endArray(); yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new java.math.BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw invalid();
        };
    }
    private static FolioCommand command(JsonObject value) {
        String kind = string(value, "kind", 32);
        return switch (kind) {
            case "edit", "send" -> {
                fields(value, "kind", "mode", "base", "epoch", "value");
                Mode mode = mode(value); long base = number(value, "base"), epoch = number(value, "epoch");
                DraftValue draft = draft(object(value, "value"));
                yield kind.equals("edit") ? new FolioCommand.Edit(mode, base, epoch, draft) : new FolioCommand.Send(mode, base, epoch, draft);
            }
            case "stop", "retry", "writeTest", "generateTests" -> {
                fields(value, "kind", "at"); Attempt at = attempt(object(value, "at"));
                yield switch (kind) { case "stop" -> new FolioCommand.Stop(at); case "retry" -> new FolioCommand.Retry(at); case "generateTests" -> new FolioCommand.GenerateTests(at); default -> new FolioCommand.WriteTest(at); };
            }
            case "mode" -> { fields(value, "kind", "mode"); yield new FolioCommand.SelectMode(mode(value)); }
            case "removeContext" -> { fields(value, "kind", "mode", "revision"); yield new FolioCommand.RemoveContext(mode(value), number(value, "revision")); }
            case "route" -> { fields(value, "kind", "to"); yield new FolioCommand.Navigate(choice(value, "to", "chat", "files", "settings")); }
            case "copy" -> {
                fields(value, "kind", "at", "part", "index");
                yield new FolioCommand.Copy(attempt(object(value, "at")), choice(value, "part", "user", "answer", "code"), integer(value, "index", 10000));
            }
            case "citation" -> {
                fields(value, "kind", "at", "index");
                yield new FolioCommand.Citation(attempt(object(value, "at")), integer(value, "index", 10000));
            }
            case "previewTest" -> {
                fields(value, "kind", "at", "tab"); yield new FolioCommand.PreviewTest(attempt(object(value, "at")), choice(value, "tab", "code", "raw"));
            }
            case "deliverCommit" -> {
                fields(value, "kind", "at", "index", "delivery"); yield new FolioCommand.DeliverCommit(attempt(object(value, "at")), integer(value, "index", 10000), choice(value, "delivery", "copy", "fill"));
            }
            case "openSource", "reindex", "removeSource" -> {
                fields(value, "kind", "id", "version"); yield new FolioCommand.Source(kind, string(value, "id", 128), number(value, "version"));
            }
            case "saveSettings" -> {
                fields(value, "kind", "edit", "providerRevision", "formRevision", "endpoint", "model", "credential");
                yield new FolioCommand.SaveSettings(string(value, "edit", 128), number(value, "providerRevision"), number(value, "formRevision"),
                        string(value, "endpoint", 8192), string(value, "model", 1024), credential(object(value, "credential")));
            }
            case "cancelSettings" -> { fields(value, "kind", "edit"); yield new FolioCommand.CancelSettings(string(value, "edit", 128)); }
            case "chooseFiles" -> { fields(value, "kind"); yield new FolioCommand.ChooseFiles(); }
            case "cancelFileTask" -> { fields(value, "kind", "id"); yield new FolioCommand.CancelFileTask(string(value, "id", 128)); }
            case "newChat" -> { fields(value, "kind"); yield new FolioCommand.NewChat(); }
            case "stage" -> { fields(value, "kind"); yield new FolioCommand.Stage(); }
            case "confirm" -> { fields(value, "kind", "id", "accept"); yield new FolioCommand.Confirm(string(value, "id", 128), bool(value, "accept")); }
            case "reading" -> { fields(value, "kind", "value"); yield new FolioCommand.Read(reading(object(value, "value"))); }
            default -> throw invalid();
        };
    }
    private static ProviderAccess.KeyChange credential(JsonObject value) {
        return switch (string(value, "kind", 32)) {
            case "keep" -> { fields(value, "kind"); yield new ProviderAccess.KeyChange.Keep(); }
            case "remove" -> { fields(value, "kind"); yield new ProviderAccess.KeyChange.Remove(); }
            case "replace" -> { fields(value, "kind", "value"); String key = string(value, "value", 16384); if (key.isBlank()) throw invalid(); yield new ProviderAccess.KeyChange.Replace(key); }
            default -> throw invalid();
        };
    }
    private static Reading reading(JsonObject value) {
        fields(value, "following", "anchor", "unseen", "expanded");
        Anchor anchor = null;
        if (!value.get("anchor").isJsonNull()) {
            JsonObject at = object(value, "anchor"); fields(at, "turn", "offset");
            JsonElement element = at.get("offset");
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) throw invalid();
            double offset = element.getAsDouble();
            if (!Double.isFinite(offset)) throw invalid();
            anchor = new Anchor(string(at, "turn", 128), offset);
        }
        if (!value.get("expanded").isJsonArray()) throw invalid();
        List<String> expanded = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray("expanded")) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString() || element.getAsString().length() > 256) throw invalid();
            expanded.add(element.getAsString());
        }
        return new Reading(bool(value, "following"), anchor, integer(value, "unseen", Integer.MAX_VALUE), List.copyOf(expanded));
    }
    private static DraftValue draft(JsonObject value) {
        fields(value, "text", "dot", "mark"); String text = string(value, "text", 524288);
        return new DraftValue(text, integer(value, "dot", text.length()), integer(value, "mark", text.length()));
    }
    private static Attempt attempt(JsonObject value) {
        fields(value, "turn", "number"); long number = number(value, "number"); if (number < 1) throw invalid();
        return new Attempt(string(value, "turn", 128), number);
    }
    private static Mode mode(JsonObject value) { return Mode.valueOf(choice(value, "mode", "ask", "drill", "tests", "commit").toUpperCase(Locale.ROOT)); }
    private static void fields(JsonObject value, String... fields) {
        if (!value.keySet().equals(Set.of(fields))) throw invalid();
    }
    private static JsonObject object(JsonObject value, String name) { JsonElement element = value.get(name); if (element == null || !element.isJsonObject()) throw invalid(); return element.getAsJsonObject(); }
    private static String string(JsonObject value, String name, int max) {
        JsonElement element = value.get(name);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString() || element.getAsString().length() > max) throw invalid();
        return element.getAsString();
    }
    private static long number(JsonObject value, String name) {
        JsonElement element = value.get(name);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) throw invalid();
        long number = element.getAsBigDecimal().longValueExact();
        if (number < 0 || number > 9_007_199_254_740_991L) throw invalid();
        return number;
    }
    private static int integer(JsonObject value, String name, int max) { long number = number(value, name); if (number > max) throw invalid(); return (int) number; }
    private static boolean bool(JsonObject value, String name) { JsonElement element = value.get(name); if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) throw invalid(); return element.getAsBoolean(); }
    private static String choice(JsonObject value, String name, String... choices) { String text = string(value, name, 32); if (!Set.of(choices).contains(text)) throw invalid(); return text; }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid Folio message."); }

    static String host(String document, String session, long hseq, Change change) {
        return object("v", 1, "document", document, "session", session, "hseq", hseq, "change", change).toString();
    }
    private static JsonObject object(Object... fields) {
        JsonObject object = new JsonObject();
        for (int index = 0; index < fields.length; index += 2) object.add((String) fields[index], project(fields[index + 1]));
        return object;
    }
    private static JsonElement project(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        return switch (value) {
            case String text -> new JsonPrimitive(text);
            case Number number -> new JsonPrimitive(number);
            case Boolean flag -> new JsonPrimitive(flag);
            case Mode mode -> new JsonPrimitive(mode.name().toLowerCase(Locale.ROOT));
            case List<?> list -> { JsonArray array = new JsonArray(); for (Object item : list) array.add(project(item)); yield array; }
            case Attempt at -> object("turn", at.turn(), "number", at.number());
            case Draft draft -> object("text", draft.text(), "dot", draft.dot(), "mark", draft.mark(), "revision", draft.revision(), "epoch", draft.epoch(), "context", draft.context());
            case Citation citation -> object("number", citation.number(), "title", citation.title(), "location", citation.location(), "excerpt", citation.excerpt());
            case Exercise exercise -> object("question", exercise.question(), "explanation", exercise.explanation());
            case Availability availability -> object("enabled", availability.enabled(), "reason", availability.reason());
            case Card card -> object("at", card.at(), "mode", card.mode(), "user", card.user(), "body", card.body(), "retry", card.retry(), "writeTest", card.writeTest(), "fillCommit", card.fillCommit(), "generateTests", card.generateTests());
            case Body.Stream body -> object("kind", "stream", "phase", body.phase(), "reasoning", body.reasoning(), "text", body.text());
            case Body.Answer body -> object("kind", "answer", "raw", body.raw(), "evidence", body.evidence(), "citations", body.citations());
            case Body.Practice body -> object("kind", "practice", "raw", body.raw(), "evidence", body.evidence(), "citations", body.citations(), "exercises", body.exercises());
            case Body.Test body -> object("kind", "test", "raw", body.raw(), "code", body.code(), "source", body.source(), "target", body.target(), "problem", body.problem());
            case Body.Commit body -> object("kind", "commit", "candidates", body.candidates(), "diff", body.diff(), "chars", body.chars(), "batches", body.batches(), "calls", body.calls());
            case Body.Failed body -> object("kind", "failed", "message", body.message(), "raw", body.raw());
            case Body.Stopped body -> object("kind", "stopped", "text", body.text());
            case Route.Chat ignored -> object("kind", "chat");
            case Route.Files ignored -> object("kind", "files");
            case Route.Settings route -> object("kind", "settings", "edit", route.edit());
            case Route.TestPreview route -> object("kind", "testPreview", "at", route.at(), "tab", route.tab());
            case Route.SourcePreview route -> object("kind", "sourcePreview", "title", route.title(), "location", route.location(), "excerpt", route.excerpt());
            case Anchor anchor -> object("turn", anchor.turn(), "offset", anchor.offset());
            case Reading reading -> object("following", reading.following(), "anchor", reading.anchor(), "unseen", reading.unseen(), "expanded", reading.expanded());
            case Provider provider -> object("revision", provider.revision(), "phase", provider.phase(), "endpoint", provider.endpoint(), "model", provider.model(), "key", provider.key(), "warning", provider.warning(), "settledForm", provider.settledForm());
            case FileRow file -> object("id", file.id(), "version", file.version(), "title", file.title(), "location", file.location(), "count", file.count(), "unit", file.unit());
            case FileTask task -> object("id", task.id(), "phase", task.phase(), "cancellable", task.cancellable());
            case Theme theme -> object("dark", theme.dark(), "reducedMotion", theme.reducedMotion(), "font", theme.font(), "fontPx", theme.fontPx(), "scale", theme.scale());
            case Confirm confirm -> object("id", confirm.id(), "text", confirm.text());
            case Shell shell -> {
                JsonObject drafts = new JsonObject();
                for (Mode mode : Mode.values()) drafts.add(mode.name().toLowerCase(Locale.ROOT), project(shell.drafts().get(mode)));
                JsonObject object = object("mode", shell.mode(), "route", shell.route(), "generation", shell.generation(), "provider", shell.provider(), "files", shell.files(), "fileTask", shell.fileTask(), "fileWarnings", shell.fileWarnings(), "reading", shell.reading(), "theme", shell.theme(), "notice", shell.notice(), "confirm", shell.confirm());
                object.add("drafts", drafts); yield object;
            }
            case Result result -> result.code() == null ? object("kind", "ok") : object("kind", "rejected", "code", result.code(), "message", result.message());
            case Change.Reset change -> object("kind", "reset", "processed", change.processed(), "shell", change.shell(), "turns", change.turns());
            case Change.ShellChanged change -> object("kind", "shell", "shell", change.shell());
            case Change.CardChanged change -> object("kind", "card", "card", change.card());
            case Change.Text change -> object("kind", "text", "at", change.at(), "offset", change.offset(), "text", change.text());
            case Change.Evict change -> object("kind", "evict", "turns", change.turns());
            case Change.Hydrated ignored -> object("kind", "hydrated");
            case Change.Ack change -> object("kind", "ack", "cseq", change.cseq(), "result", change.result());
            default -> throw new IllegalArgumentException("Not a Folio projection.");
        };
    }
    private FolioCodec() {}
}
