package cst3492.courseta.index;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CourseIndex {
    private final Map<String, CourseChunk> byId = new LinkedHashMap<>();
    private final Map<String, List<String>> tokenToIds = new HashMap<>();

    public int size() {
        return byId.size();
    }

    public void removeSource(String sourceDocument) {
        List<String> drop = new ArrayList<>();
        for (CourseChunk chunk : byId.values()) {
            if (chunk.sourceDocument().equals(sourceDocument)) {
                drop.add(chunk.id());
            }
        }
        for (String id : drop) {
            byId.remove(id);
        }
        rebuildTokens();
    }

    public void upsert(CourseChunk chunk) {
        byId.put(chunk.id(), chunk);
        rebuildTokens();
    }

    public List<CourseChunk> top(String query, int limit) {
        Set<String> queryTokens = Tokens.unique(query);
        List<Scored> scored = new ArrayList<>();
        for (CourseChunk chunk : byId.values()) {
            int score = 0;
            for (String token : queryTokens) {
                List<String> ids = tokenToIds.get(token);
                if (ids != null && ids.contains(chunk.id())) {
                    score++;
                }
            }
            if (score > 0) {
                scored.add(new Scored(score, chunk));
            }
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed().thenComparing(s -> s.chunk().id()));
        List<CourseChunk> out = new ArrayList<>();
        for (Scored item : scored) {
            if (out.size() == limit) {
                break;
            }
            out.add(item.chunk());
        }
        return out;
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        List<String> lines = new ArrayList<>();
        for (CourseChunk chunk : byId.values()) {
            lines.add(String.join("\t",
                    chunk.id(),
                    chunk.sourceDocument(),
                    chunk.scopeKind().name(),
                    Integer.toString(chunk.scopeNumber()),
                    URLEncoder.encode(chunk.text(), StandardCharsets.UTF_8)));
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    public static CourseIndex load(Path file) throws IOException {
        CourseIndex index = new CourseIndex();
        if (!Files.exists(file)) {
            return index;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t", 5);
            if (parts.length < 5) {
                continue;
            }
            String text = URLDecoder.decode(parts[4], StandardCharsets.UTF_8);
            if (text.isBlank()) {
                continue;
            }
            CourseChunk chunk = new CourseChunk(
                    parts[0],
                    parts[1],
                    ScopeKind.valueOf(parts[2]),
                    Integer.parseInt(parts[3]),
                    text);
            index.byId.put(chunk.id(), chunk);
        }
        index.rebuildTokens();
        return index;
    }

    private void rebuildTokens() {
        tokenToIds.clear();
        for (CourseChunk chunk : byId.values()) {
            for (String token : Tokens.unique(chunk.text())) {
                tokenToIds.computeIfAbsent(token, key -> new ArrayList<>()).add(chunk.id());
            }
        }
    }

    private record Scored(int score, CourseChunk chunk) {
    }
}
