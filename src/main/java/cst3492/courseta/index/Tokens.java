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

    private Tokens() {
    }

    public static Set<String> unique(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        Matcher matcher = WORDS.matcher(text);
        while (matcher.find()) {
            out.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        String compact = text.replaceAll("\\s+", "");
        for (int i = 0; i + 1 < compact.length(); i++) {
            out.add(compact.substring(i, i + 2));
        }
        return out;
    }

    public static List<String> list(String text) {
        return new ArrayList<>(unique(text));
    }
}
