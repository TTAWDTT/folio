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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CourseIndex {
    private static final double K1 = 1.2;
    private static final double B = 0.75;
    // One distinctive term in a single-chunk index scores about 0.29. Overlap below this is not a hit.
    private static final double MIN_SCORE = 0.2;

    private final Map<String, CourseChunk> byId = new LinkedHashMap<>();
    private final Map<String, Map<String, Integer>> termFreq = new HashMap<>();
    private final Map<String, Integer> docLength = new HashMap<>();
    private int totalTerms;

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
        if (byId.isEmpty() || limit == 0) {
            return List.of();
        }
        Set<String> queryTerms = new LinkedHashSet<>(Tokens.terms(query));
        if (queryTerms.isEmpty() || totalTerms == 0) {
            return List.of();
        }
        double avgdl = (double) totalTerms / byId.size();
        List<Scored> scored = new ArrayList<>();
        for (CourseChunk chunk : byId.values()) {
            double score = score(chunk.id(), queryTerms, avgdl);
            if (score >= MIN_SCORE) {
                scored.add(new Scored(score, chunk));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed().thenComparing(s -> s.chunk().id()));
        List<CourseChunk> out = new ArrayList<>();
        for (Scored item : scored) {
            if (out.size() == limit) {
                break;
            }
            out.add(item.chunk());
        }
        return out;
    }

    private double score(String chunkId, Set<String> queryTerms, double avgdl) {
        int length = docLength.getOrDefault(chunkId, 0);
        if (length == 0) {
            return 0;
        }
        double total = 0;
        int docs = byId.size();
        for (String term : queryTerms) {
            Map<String, Integer> posting = termFreq.get(term);
            if (posting == null) {
                continue;
            }
            Integer freq = posting.get(chunkId);
            if (freq == null || freq == 0) {
                continue;
            }
            int df = posting.size();
            double idf = Math.log(1.0 + (docs - df + 0.5) / (df + 0.5));
            double denom = freq + K1 * (1.0 - B + B * length / avgdl);
            total += idf * (freq * (K1 + 1.0)) / denom;
        }
        return total;
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
        termFreq.clear();
        docLength.clear();
        totalTerms = 0;
        for (CourseChunk chunk : byId.values()) {
            List<String> terms = Tokens.terms(chunk.text());
            docLength.put(chunk.id(), terms.size());
            totalTerms += terms.size();
            Map<String, Integer> counts = new HashMap<>();
            for (String term : terms) {
                counts.merge(term, 1, Integer::sum);
            }
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                termFreq.computeIfAbsent(entry.getKey(), key -> new HashMap<>()).put(chunk.id(), entry.getValue());
            }
        }
    }

    private record Scored(double score, CourseChunk chunk) {
    }
}
