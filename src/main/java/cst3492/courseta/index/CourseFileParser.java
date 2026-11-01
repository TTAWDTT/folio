package cst3492.courseta.index;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.sax.ToXMLContentHandler;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CourseFileParser {
    public List<CourseChunk> parse(Path file, ClassLoader loader) throws Exception {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".pptx")) {
            return chunksFromPptx(file);
        }
        String xml = toXml(file, loader);
        return chunksFromXml(file.getFileName().toString(), xml);
    }

    static List<CourseChunk> chunksFromPptx(Path file) throws Exception {
        String source = file.getFileName().toString();
        List<CourseChunk> chunks = new ArrayList<>();
        try (InputStream input = Files.newInputStream(file); XMLSlideShow show = new XMLSlideShow(input)) {
            List<XSLFSlide> slides = show.getSlides();
            for (int i = 0; i < slides.size(); i++) {
                String text = slideText(slides.get(i));
                if (text.isBlank()) {
                    continue;
                }
                int number = i + 1;
                chunks.addAll(CourseChunks.split(source, ScopeKind.SLIDE, number, text));
            }
        }
        return chunks;
    }

    private static String slideText(XSLFSlide slide) {
        StringBuilder builder = new StringBuilder();
        appendShapes(builder, slide.getShapes());
        XSLFNotes notes = slide.getNotes();
        if (notes != null) {
            appendShapes(builder, notes.getShapes());
        }
        return builder.toString().strip();
    }

    private static void appendShapes(StringBuilder builder, List<XSLFShape> shapes) {
        for (XSLFShape shape : shapes) {
            if (shape instanceof XSLFTextShape textShape) {
                String text = textShape.getText();
                if (text != null && !text.isBlank()) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(text.strip());
                }
            }
        }
    }

    static List<CourseChunk> chunksFromXml(String sourceDocument, String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        NodeList divs = document.getElementsByTagName("div");
        List<Raw> raws = new ArrayList<>();
        for (int i = 0; i < divs.getLength(); i++) {
            Node node = divs.item(i);
            if (!(node instanceof Element element)) {
                continue;
            }
            String kind = element.getAttribute("class");
            if ("page".equals(kind) || "slide-content".equals(kind) || "slide-notes".equals(kind)) {
                raws.add(new Raw(kind, textOf(element)));
            }
        }
        List<CourseChunk> chunks = new ArrayList<>();
        int page = 0;
        int slide = 0;
        for (int i = 0; i < raws.size(); i++) {
            Raw raw = raws.get(i);
            if ("slide-notes".equals(raw.kind())) {
                continue;
            }
            String text = raw.text();
            if ("slide-content".equals(raw.kind()) && i + 1 < raws.size() && "slide-notes".equals(raws.get(i + 1).kind())) {
                String notes = raws.get(i + 1).text();
                if (!notes.isBlank()) {
                    text = text.isBlank() ? notes : text + "\n" + notes;
                }
            }
            if (text.isBlank()) {
                if ("page".equals(raw.kind())) {
                    page++;
                } else if ("slide-content".equals(raw.kind())) {
                    slide++;
                }
                continue;
            }
            if ("page".equals(raw.kind())) {
                page++;
                chunks.addAll(CourseChunks.split(sourceDocument, ScopeKind.PAGE, page, text));
            } else if ("slide-content".equals(raw.kind())) {
                slide++;
                chunks.addAll(CourseChunks.split(sourceDocument, ScopeKind.SLIDE, slide, text));
            }
        }
        return chunks;
    }

    private static String textOf(Element element) {
        return element.getTextContent().replace('\u00a0', ' ').strip();
    }

    private static String toXml(Path file, ClassLoader loader) throws Exception {
        Parser parser = new AutoDetectParser(new org.apache.tika.parser.DefaultParser(loader));
        OfficeParserConfig office = new OfficeParserConfig();
        office.setIncludeSlideMasterContent(false);
        ParseContext context = new ParseContext();
        context.set(OfficeParserConfig.class, office);
        context.set(Parser.class, parser);
        ToXMLContentHandler handler = new ToXMLContentHandler();
        try (TikaInputStream input = TikaInputStream.get(file)) {
            parser.parse(input, handler, new Metadata(), context);
        }
        return handler.toString();
    }

    private record Raw(String kind, String text) {
    }
}
