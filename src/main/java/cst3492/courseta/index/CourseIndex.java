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
    // A term in 17 of 20 chunks scores about 0.18. A term in every chunk scores about 0.02.
    // 0.10 drops the second case and still keeps the first. 0.2 was too high for the first.
    private static final double MIN_SCORE = 0.10;
    // Also drop hits that are only a small fraction of this query's best score.
    private static final double RELATIVE_KEEP = 0.25;

    private final Map<String, CourseChunk> byId = new LinkedHashMap<>();
    private final Map<String, Map<String, Integer>> termFreq = new HashMap<>();
    private final Map<String, Integer> docLength = new HashMap<>();
    private int totalTerms;

    public int size() {
        return byId.size();
    }

    public synchronized void removeSource(String sourceDocument) {
        List<CourseChunk> drop = new ArrayList<>();
        for (CourseChunk chunk : byId.values()) {
            if (chunk.sourceDocument().equals(sourceDocument)) {
                drop.add(chunk);
            }
        }
        for (CourseChunk chunk : drop) {
            byId.remove(chunk.id());
            subtract(chunk);
        }
    }

    public synchronized void upsert(CourseChunk chunk) {
        CourseChunk previous = byId.put(chunk.id(), chunk);
        if (previous != null) {
            subtract(previous);
        }
        add(chunk);
    }

    public synchronized List<CourseChunk> top(String query, int limit) {
        if (byId.isEmpty() || limit == 0) {
            return List.of();
        }
        Set<String> queryTerms = new LinkedHashSet<>(Tokens.terms(query));
        if (queryTerms.isEmpty() || totalTerms == 0) {
            return List.of();
        }
        double avgdl = (double) totalTerms / byId.size();
        int docs = byId.size();
        Map<String, Double> scores = new HashMap<>();
        for (String term : queryTerms) {
            Map<String, Integer> posting = termFreq.get(term);
            if (posting == null || posting.isEmpty()) {
                continue;
            }
            int df = posting.size();
            double idf = Math.log(1.0 + (docs - df + 0.5) / (df + 0.5));
            for (Map.Entry<String, Integer> entry : posting.entrySet()) {
                int freq = entry.getValue();
                int length = docLength.getOrDefault(entry.getKey(), 0);
                if (freq == 0 || length == 0) {
                    continue;
                }
                double denom = freq + K1 * (1.0 - B + B * length / avgdl);
                double contribution = idf * (freq * (K1 + 1.0)) / denom;
                scores.merge(entry.getKey(), contribution, Double::sum);
            }
        }
        List<Scored> scored = new ArrayList<>();
        double best = 0;
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            if (entry.getValue() < MIN_SCORE) {
                continue;
            }
            CourseChunk chunk = byId.get(entry.getKey());
            if (chunk != null) {
                best = Math.max(best, entry.getValue());
                scored.add(new Scored(entry.getValue(), chunk));
            }
        }
        if (scored.isEmpty()) {
            return List.of();
        }
        double cutoff = best * RELATIVE_KEEP;
        scored.sort(Comparator.comparingDouble(Scored::score).reversed().thenComparing(s -> s.chunk().id()));
        List<CourseChunk> out = new ArrayList<>();
        for (Scored item : scored) {
            if (item.score() < cutoff || out.size() == limit) {
                break;
            }
            out.add(item.chunk());
        }
        return out;
    }

    public synchronized void save(Path file) throws IOException {
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

    private void add(CourseChunk chunk) {
        List<String> terms = Tokens.terms(chunk.text());
        docLength.put(chunk.id(), terms.size());
        totalTerms += terms.size();
        for (Map.Entry<String, Integer> entry : counts(terms).entrySet()) {
            termFreq.computeIfAbsent(entry.getKey(), key -> new HashMap<>()).put(chunk.id(), entry.getValue());
        }
    }

    private void subtract(CourseChunk chunk) {
        int length = docLength.getOrDefault(chunk.id(), 0);
        docLength.remove(chunk.id());
        totalTerms = Math.max(0, totalTerms - length);
        for (String term : counts(Tokens.terms(chunk.text())).keySet()) {
            Map<String, Integer> posting = termFreq.get(term);
            if (posting == null) {
                continue;
            }
            posting.remove(chunk.id());
            if (posting.isEmpty()) {
                termFreq.remove(term);
            }
        }
    }

    private void rebuildTokens() {
        termFreq.clear();
        docLength.clear();
        totalTerms = 0;
        for (CourseChunk chunk : byId.values()) {
            add(chunk);
        }
    }

    private static Map<String, Integer> counts(List<String> terms) {
        Map<String, Integer> counts = new HashMap<>();
        for (String term : terms) {
            counts.merge(term, 1, Integer::sum);
        }
        return counts;
    }

    private record Scored(double score, CourseChunk chunk) {
    }
}
