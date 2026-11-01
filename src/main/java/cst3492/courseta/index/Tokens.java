package cst3492.courseta.index;

import java.util.ArrayList;
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
            if (isCjk(points[i]) && isCjk(points[i + 1])) {
                out.add(new String(points, i, 2));
            }
        }
        return out;
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

    private static boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}
