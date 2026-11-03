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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CourseIndex implements CourseSearch {
    private static final double K1 = 1.2;
    private static final double B = 0.75;
    // A chunk must cover at least half of the question's coverage units. For Chinese that
    // unit is a run of content Han characters, not each bigram. One shared word in a longer
    // question is not a hit, at any document frequency.
    static final double MIN_COVERAGE = 0.5;
    // Among chunks that clear that bar, drop scores below this fraction of the best score.
    static final double RELATIVE_KEEP = 0.25;

    private final Map<String, CourseChunk> byId = new LinkedHashMap<>();
    private final Map<String, Map<String, Integer>> termFreq = new HashMap<>();
    private final Map<String, Integer> docLength = new HashMap<>();
    private int totalTerms;

    public synchronized int size() {
        return byId.size();
    }

    public synchronized List<CourseChunk> chunks() {
        return List.copyOf(byId.values());
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

    /**
     * Drops this source, and the older bare-name row when {@code sourceDocument} is a path.
     * Indexes written before paths were stored used only the file name, so re-indexing
     * {@code /lectures/notes.pdf} must also drop {@code notes.pdf}. A different directory
     * with the same file name is left alone. Deleting a bare name still removes that row.
     */
    public synchronized void removeHandout(String sourceDocument) {
        if (sourceDocument == null || sourceDocument.isBlank()) {
            return;
        }
        removeSource(sourceDocument);
        String legacy = legacyFileName(sourceDocument);
        if (legacy != null) {
            removeSource(legacy);
        }
    }

    static String legacyFileName(String sourceDocument) {
        int slash = Math.max(sourceDocument.lastIndexOf('/'), sourceDocument.lastIndexOf('\\'));
        if (slash < 0 || slash >= sourceDocument.length() - 1) {
            return null;
        }
        return sourceDocument.substring(slash + 1);
    }

    public synchronized void upsert(CourseChunk chunk) {
        CourseChunk previous = byId.put(chunk.id(), chunk);
        if (previous != null) {
            subtract(previous);
        }
        add(chunk);
    }

    public synchronized List<CourseChunk> top(String query, int limit) {
        if (byId.isEmpty() || limit <= 0) {
            return List.of();
        }
        Set<String> queryTerms = new LinkedHashSet<>(Tokens.terms(query));
        List<List<String>> coverageGroups = applicable(Tokens.coverageGroups(query));
        if (queryTerms.isEmpty() || coverageGroups.isEmpty() || totalTerms == 0) {
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
        Map<String, Integer> covered = new HashMap<>();
        for (List<String> group : coverageGroups) {
            Set<String> seen = new HashSet<>();
            for (String term : group) {
                Map<String, Integer> posting = termFreq.get(term);
                if (posting == null) {
                    continue;
                }
                for (String id : posting.keySet()) {
                    if (seen.add(id)) {
                        covered.merge(id, 1, Integer::sum);
                    }
                }
            }
        }
        List<Scored> scored = new ArrayList<>();
        double best = 0;
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            int matched = covered.getOrDefault(entry.getKey(), 0);
            if (!enoughCoverage(matched, coverageGroups.size()) || entry.getValue() <= 0) {
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

    static boolean enoughCoverage(int matchedTerms, int queryTerms) {
        return queryTerms > 0 && matchedTerms >= queryTerms * MIN_COVERAGE;
    }

    /** An English lecture has no Chinese bigrams. A Chinese question around a Latin term
     *  such as JUnit would otherwise require those bigrams and match nothing. When none of
     *  the Chinese groups appear anywhere in the index, judge the Latin groups alone.
     *  Unattested English words still count, so one shared word in a longer question misses. */
    private List<List<String>> applicable(List<List<String>> groups) {
        List<List<String>> latin = new ArrayList<>();
        List<List<String>> han = new ArrayList<>();
        for (List<String> group : groups) {
            if (group.stream().allMatch(CourseIndex::latinTerm)) latin.add(group);
            else han.add(group);
        }
        if (!han.isEmpty() && !anyAttested(han) && anyAttested(latin)) {
            return latin;
        }
        return groups;
    }

    private boolean anyAttested(List<List<String>> groups) {
        for (List<String> group : groups) {
            for (String term : group) {
                Map<String, Integer> posting = termFreq.get(term);
                if (posting != null && !posting.isEmpty()) return true;
            }
        }
        return false;
    }

    private static boolean latinTerm(String term) {
        for (int i = 0; i < term.length(); i++) {
            if (term.charAt(i) > 127) return false;
        }
        return !term.isEmpty();
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
