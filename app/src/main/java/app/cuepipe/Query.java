package app.cuepipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a shelf query into an FTS4 MATCH string.
 * The index uses the porter tokenizer, so English stemming happens in SQLite.
 * shortcut: Concepts is a small synonym list, not WordNet. Meaning is a separate vector table.
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

    /** A stretch of the quote the query names. A concept synonym is related. */
    public static final class Mark {
        public final int start;
        public final int end;
        public final boolean related;

        public Mark(int start, int end, boolean related) {
            this.start = start;
            this.end = end;
            this.related = related;
        }
    }

    /**
     * Ranges to paint in a hit. The surface word counts, and so does a short
     * English ending (s, es, ed, ing, er, ly). A Porter stem that is not one
     * of those stays unmarked.
     */
    public static List<Mark> marks(String text, String raw, boolean concepts) {
        List<Mark> out = new ArrayList<>();
        if (text == null || text.isEmpty() || raw == null || raw.trim().isEmpty()) {
            return out;
        }
        List<String> words = new ArrayList<>();
        List<Boolean> related = new ArrayList<>();
        List<Boolean> prefix = new ArrayList<>();
        for (String token : tokenize(raw.trim())) {
            if ("AND".equals(token) || "OR".equals(token) || "NOT".equals(token)) {
                continue;
            }
            if (token.startsWith("\"")) {
                String phrase = token.length() > 1 && token.endsWith("\"")
                        ? token.substring(1, token.length() - 1) : token.substring(1);
                markPhrase(text, phrase, out);
                continue;
            }
            boolean star = token.endsWith("*") && token.length() > 1;
            String word = token;
            if (star || (token.endsWith("~") && token.length() > 1)) {
                word = token.substring(0, token.length() - 1);
            }
            String safe = word.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
            if (safe.isEmpty()) {
                continue;
            }
            words.add(safe);
            related.add(false);
            prefix.add(star);
            if (concepts) {
                String[] synonyms = CONCEPTS.get(safe);
                if (synonyms != null) {
                    for (String synonym : synonyms) {
                        words.add(synonym);
                        related.add(true);
                        prefix.add(false);
                    }
                }
            }
        }
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            if (!Character.isLetterOrDigit(cp)) {
                i += Character.charCount(cp);
                continue;
            }
            int start = i;
            while (i < text.length()) {
                int next = text.codePointAt(i);
                if (!Character.isLetterOrDigit(next)) {
                    break;
                }
                i += Character.charCount(next);
            }
            int best = -1;
            String surface = text.substring(start, i);
            for (int w = 0; w < words.size(); w++) {
                if (!same(surface, words.get(w), prefix.get(w))) {
                    continue;
                }
                if (best < 0 || (related.get(best) && !related.get(w))) {
                    best = w;
                }
            }
            if (best >= 0 && !covered(out, start, i)) {
                out.add(new Mark(start, i, related.get(best)));
            }
        }
        return out;
    }

    private static void markPhrase(String text, String phrase, List<Mark> out) {
        if (phrase.isEmpty()) {
            return;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String needle = phrase.toLowerCase(Locale.ROOT);
        int from = 0;
        while (from < lower.length()) {
            int at = lower.indexOf(needle, from);
            if (at < 0) {
                return;
            }
            if (!covered(out, at, at + phrase.length())) {
                out.add(new Mark(at, at + phrase.length(), false));
            }
            from = at + needle.length();
        }
    }

    private static boolean covered(List<Mark> marks, int start, int end) {
        for (Mark mark : marks) {
            if (start < mark.end && end > mark.start) {
                return true;
            }
        }
        return false;
    }

    /** The query word, a short ending on it, or a prefix when the token had a star. */
    private static boolean same(String surface, String needle, boolean prefix) {
        String word = surface.toLowerCase(Locale.ROOT);
        if (prefix) {
            return needle.length() >= 2 && word.startsWith(needle);
        }
        if (word.equals(needle)) {
            return true;
        }
        if (needle.length() < 3 || !word.startsWith(needle)) {
            return false;
        }
        String rest = word.substring(needle.length());
        return rest.equals("s") || rest.equals("es") || rest.equals("ed") || rest.equals("ing")
                || rest.equals("er") || rest.equals("ly") || rest.equals("ers");
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
