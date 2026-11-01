package cst3492.courseta.index;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Tokens {
    private static final Pattern WORDS = Pattern.compile("[A-Za-z0-9_]+");
    private static final Set<String> STOP = Set.of(
            "a", "about", "above", "after", "again", "against", "all", "also", "am", "an", "and", "any", "are", "as", "at",
            "be", "been", "before", "being", "below", "between", "both", "but", "by", "can", "could", "describe", "did",
            "do", "does", "doing", "done", "down", "during", "each", "else", "explain", "few", "for", "from", "further",
            "had", "has", "have", "having", "he", "her", "here", "his", "how", "i", "if", "in", "into", "is", "it", "its",
            "itself", "just", "me", "might", "more", "most", "must", "my", "need", "needs", "no", "nor", "not", "of", "off",
            "on", "once", "only", "or", "other", "our", "out", "over", "own", "please", "same", "shall", "she", "should",
            "so", "some", "such", "tell", "than", "that", "the", "their", "them", "then", "there", "these", "they", "this",
            "those", "through", "to", "too", "under", "until", "up", "use", "used", "uses", "using", "very", "via", "was",
            "we", "were", "what", "when", "where", "which", "while", "who", "whom", "whose", "why", "will", "with",
            "within", "without", "would", "you", "your");
    /**
     * Closed-class characters. A bigram is dropped only when both of its characters are in this set,
     * so 我们 / 们可 / 可以 disappear together. Characters that begin technical words (并, 向, 不, 能, …)
     * are not in the set, and a mixed pair such as 并发 is kept.
     */
    private static final String CJK_FUNCTION =
            "的了是在这那个和与或而但也都很还又把从为以会想去来到吗呢吧啊呀嘛么怎什哪谁何如若则所且及其之于"
                    + "我你他她它们就可没让";
    /**
     * Left out of the coverage count only. They stay in the index, so a query that is just
     * "one-to-one" still matches, but they do not dilute "explain polymorphism" or OneToMany.
     */
    private static final Set<String> COVERAGE_SKIP = Set.of(
            "confused", "give", "simple", "one", "many", "kindly", "briefly", "describe");
    /**
     * Courtesy words removed from a Chinese coverage span. They are not removed from the
     * index, so 请求 and 性能 stay searchable. Longer phrases are matched first.
     */
    private static final int[][] COVERAGE_FILLER = fillerPoints(
            "能不能", "请问", "详细", "麻烦", "了解", "深入", "一下", "问题", "回事", "到底",
            "请", "讲", "地", "给");

    private Tokens() {
    }

    public static List<String> terms(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        Matcher matcher = WORDS.matcher(text);
        while (matcher.find()) {
            addWord(out, matcher.group());
        }
        int[] points = text.codePoints().toArray();
        for (int i = 0; i + 1 < points.length; i++) {
            if (isCjk(points[i]) && isCjk(points[i + 1]) && !bothFunction(points[i], points[i + 1])) {
                out.add(new String(points, i, 2));
            }
        }
        return out;
    }

    /**
     * Coverage units for a question. English words are one unit each, minus courtesy and
     * filler words. Chinese is one unit per run of content Han characters, covered when any
     * bigram inside that run matches. Courtesy runs such as 请详细讲讲 are not units.
     * A question with no such unit, such as 不可变 or 请求, falls back to its indexed terms.
     */
    public static List<List<String>> coverageGroups(String text) {
        LinkedHashSet<String> all = new LinkedHashSet<>(terms(text));
        List<List<String>> groups = new ArrayList<>();
        for (String term : all) {
            if (isLatinTerm(term) && !COVERAGE_SKIP.contains(term)) {
                groups.add(List.of(term));
            }
        }
        groups.addAll(hanGroups(text));
        if (groups.isEmpty()) {
            for (String term : all) {
                groups.add(List.of(term));
            }
        }
        return groups;
    }

    public static List<String> coverageTerms(String text) {
        List<String> flat = new ArrayList<>();
        for (List<String> group : coverageGroups(text)) {
            flat.addAll(group);
        }
        return flat;
    }

    public static Set<String> unique(String text) {
        return new LinkedHashSet<>(terms(text));
    }

    public static List<String> list(String text) {
        return new ArrayList<>(unique(text));
    }

    private static void addWord(List<String> out, String raw) {
        String word = raw.toLowerCase(Locale.ROOT);
        if (word.length() < 2 || STOP.contains(word)) {
            return;
        }
        String stemmed = stem(word);
        if (stemmed.length() < 2 || STOP.contains(stemmed)) {
            return;
        }
        out.add(stemmed);
    }

    private static String stem(String word) {
        if (word.length() < 4) {
            return word;
        }
        if (word.endsWith("sses")) {
            return word.substring(0, word.length() - 2);
        }
        if (word.length() > 4 && word.endsWith("ies")) {
            return word.substring(0, word.length() - 3) + "y";
        }
        if (word.endsWith("ss") || !word.endsWith("s")) {
            return word;
        }
        return word.substring(0, word.length() - 1);
    }

    private static boolean isLatinTerm(String term) {
        int[] points = term.codePoints().toArray();
        if (points.length == 0) {
            return false;
        }
        for (int point : points) {
            if (isCjk(point)) {
                return false;
            }
        }
        return true;
    }

    private static List<List<String>> hanGroups(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        int[] points = text.codePoints().toArray();
        List<List<String>> groups = new ArrayList<>();
        List<Integer> run = new ArrayList<>();
        for (int i = 0; i <= points.length; i++) {
            boolean content = i < points.length && isHanContent(points[i]);
            if (content) {
                run.add(points[i]);
                continue;
            }
            for (List<Integer> part : stripFillers(run)) {
                if (part.size() < 2) {
                    continue;
                }
                List<String> bigrams = new ArrayList<>();
                for (int j = 0; j + 1 < part.size(); j++) {
                    bigrams.add(new String(new int[] {part.get(j), part.get(j + 1)}, 0, 2));
                }
                groups.add(bigrams);
            }
            run.clear();
        }
        return groups;
    }

    private static List<List<Integer>> stripFillers(List<Integer> run) {
        List<List<Integer>> parts = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        int i = 0;
        while (i < run.size()) {
            int skip = fillerLength(run, i);
            if (skip > 0) {
                if (current.size() >= 2) {
                    parts.add(current);
                }
                current = new ArrayList<>();
                i += skip;
            } else {
                current.add(run.get(i));
                i++;
            }
        }
        if (current.size() >= 2) {
            parts.add(current);
        }
        return parts;
    }

    private static int fillerLength(List<Integer> run, int at) {
        for (int[] filler : COVERAGE_FILLER) {
            if (at + filler.length > run.size()) {
                continue;
            }
            boolean match = true;
            for (int k = 0; k < filler.length; k++) {
                if (run.get(at + k) != filler[k]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return filler.length;
            }
        }
        return 0;
    }

    private static int[][] fillerPoints(String... words) {
        int[][] points = new int[words.length][];
        for (int i = 0; i < words.length; i++) {
            points[i] = words[i].codePoints().toArray();
        }
        Arrays.sort(points, (left, right) -> Integer.compare(right.length, left.length));
        return points;
    }

    private static boolean isHanContent(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN
                && CJK_FUNCTION.indexOf(codePoint) < 0;
    }

    private static boolean bothFunction(int left, int right) {
        return CJK_FUNCTION.indexOf(left) >= 0 && CJK_FUNCTION.indexOf(right) >= 0;
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}
