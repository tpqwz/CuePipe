package app.cuepipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a shelf query into an FTS4 MATCH string.
 * The index uses the porter tokenizer, so English stemming happens in SQLite.
 * shortcut: Concepts is a small synonym list, not WordNet. Meaning is not here.
 */
public final class Query {
    private static final Map<String, String[]> CONCEPTS = new HashMap<>();

    static {
        put("big", "large", "great");
        put("small", "little", "tiny");
        put("fast", "quick", "rapid");
        put("say", "speak", "tell");
        put("movie", "film", "picture");
        put("video", "clip", "film");
        put("start", "begin", "open");
        put("end", "finish", "stop");
        put("good", "fine", "great");
        put("bad", "poor", "awful");
    }

    private Query() {
    }

    public static String match(String raw, boolean fuzzy, boolean concepts) {
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }
        List<String> tokens = tokenize(raw.trim());
        StringBuilder out = new StringBuilder();
        for (String token : tokens) {
            if (out.length() > 0) {
                out.append(' ');
            }
            if ("AND".equals(token) || "OR".equals(token) || "NOT".equals(token)) {
                out.append(token);
                continue;
            }
            out.append(expand(token, fuzzy, concepts));
        }
        return out.toString().trim();
    }

    static String expand(String token, boolean fuzzy, boolean concepts) {
        if (token.startsWith("\"")) {
            return token;
        }
        boolean prefix = token.endsWith("*") && token.length() > 1;
        boolean edit = fuzzy && token.endsWith("~") && token.length() > 1;
        String word = token;
        if (prefix) {
            word = token.substring(0, token.length() - 1);
        }
        if (edit) {
            word = token.substring(0, token.length() - 1);
        }
        String safe = word.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
        if (safe.isEmpty()) {
            return "\"" + word.replace("\"", "") + "\"";
        }
        List<String> alts = new ArrayList<>();
        alts.add(safe + (prefix ? "*" : ""));
        if (edit) {
            for (String alt : edits(safe)) {
                if (!alt.equals(safe) && alt.length() > 1) {
                    alts.add(alt);
                }
            }
        }
        if (concepts) {
            String[] synonyms = CONCEPTS.get(safe);
            if (synonyms != null) {
                for (String synonym : synonyms) {
                    alts.add(synonym);
                }
            }
        }
        if (alts.size() == 1) {
            return alts.get(0);
        }
        return "(" + String.join(" OR ", alts) + ")";
    }

    /** One-edit neighbors. Capped so a long word does not build a giant query. */
    static List<String> edits(String word) {
        List<String> out = new ArrayList<>();
        if (word.length() > 8) {
            return out;
        }
        String letters = "abcdefghijklmnopqrstuvwxyz";
        for (int i = 0; i < word.length(); i++) {
            out.add(word.substring(0, i) + word.substring(i + 1));
            if (i + 1 < word.length()) {
                out.add(word.substring(0, i) + word.charAt(i + 1) + word.charAt(i) + word.substring(i + 2));
            }
            for (int j = 0; j < letters.length(); j++) {
                char c = letters.charAt(j);
                if (c != word.charAt(i)) {
                    out.add(word.substring(0, i) + c + word.substring(i + 1));
                }
                out.add(word.substring(0, i) + c + word.substring(i));
            }
        }
        for (int j = 0; j < letters.length(); j++) {
            out.add(word + letters.charAt(j));
        }
        return out;
    }

    private static List<String> tokenize(String raw) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '"') {
                int end = raw.indexOf('"', i + 1);
                if (end < 0) {
                    tokens.add(raw.substring(i));
                    break;
                }
                tokens.add(raw.substring(i, end + 1));
                i = end + 1;
                continue;
            }
            int j = i;
            while (j < raw.length() && !Character.isWhitespace(raw.charAt(j))) {
                j++;
            }
            tokens.add(raw.substring(i, j));
            i = j;
        }
        return tokens;
    }

    private static void put(String word, String... synonyms) {
        CONCEPTS.put(word, synonyms);
    }
}
