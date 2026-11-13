package app.cuepipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns caption cues into paragraphs. A pause, or a long stretch that has
 * already finished a sentence, starts the next paragraph. The first cue's
 * time is the heading. Latin cues gain a period where a new sentence starts
 * and at the end of a paragraph. CJK cues are not given Latin periods.
 */
public final class Prose {
    private static final int PAUSE_MS = 1500;
    private static final int SOFT = 480;
    private static final int HARD = 700;

    private Prose() {
    }

    /** One paragraph. spanIndex is the first cue, which is where a tap seeks. */
    public static final class Block {
        public final int spanIndex;
        public final int t0;
        public final int t1;
        public final String text;

        public Block(int spanIndex, int t0, int t1, String text) {
            this.spanIndex = spanIndex;
            this.t0 = t0;
            this.t1 = t1;
            this.text = text;
        }
    }

    public static List<Block> blocks(List<Cues.Span> spans) {
        List<Block> out = new ArrayList<>();
        if (spans == null) {
            return out;
        }
        List<String> cues = new ArrayList<>();
        int start = -1;
        int t0 = 0;
        int prevEnd = 0;
        int chars = 0;
        for (int i = 0; i < spans.size(); i++) {
            Cues.Span span = spans.get(i);
            String text = span.text == null ? "" : span.text.trim();
            if (text.isEmpty()) {
                continue;
            }
            boolean pause = start >= 0 && span.t0 - prevEnd > PAUSE_MS;
            boolean room = chars > HARD || (chars > SOFT && endsSentence(join(cues)));
            if (start >= 0 && (pause || room)) {
                out.add(new Block(start, t0, prevEnd, join(cues)));
                cues.clear();
                start = -1;
                chars = 0;
            }
            if (start < 0) {
                start = i;
                t0 = span.t0;
            }
            cues.add(text);
            chars += text.length() + 1;
            prevEnd = Math.max(prevEnd, span.t1);
        }
        if (start >= 0) {
            out.add(new Block(start, t0, prevEnd, join(cues)));
        }
        return out;
    }

    /** The other language, over the same stretch of time, in one paragraph. */
    public static String nested(List<Cues.Span> spans, int t0, int t1) {
        if (spans == null || spans.isEmpty()) {
            return "";
        }
        List<String> cues = new ArrayList<>();
        for (Cues.Span span : spans) {
            if (span.t1 <= t0 || span.t0 >= t1) {
                continue;
            }
            if (span.text == null || span.text.trim().isEmpty()) {
                continue;
            }
            cues.add(span.text.trim());
        }
        return join(cues);
    }

    /** The paragraph that contains this cue, or the latest paragraph that started. */
    public static int blockAt(List<Block> blocks, int spanIndex) {
        int at = -1;
        if (blocks == null) {
            return -1;
        }
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).spanIndex > spanIndex) {
                break;
            }
            at = i;
        }
        return at;
    }

    /**
     * Character range of the word under timeMs, or {@code {-1,-1}}.
     * The cue's time is shared across its words by length. A CJK character
     * is its own word. Before the cue, nothing is marked.
     */
    public static int[] wordAt(String text, int t0, int t1, int timeMs) {
        if (text == null || text.isEmpty() || timeMs < t0) {
            return NONE;
        }
        return pick(wordsIn(text, 0, text.length(), t0, Math.max(t1, t0 + 1)), timeMs);
    }

    /**
     * The same range inside the paragraph text. A gap between cues keeps the
     * word just played. Indexes match {@link Block#text}.
     */
    public static int[] wordAt(List<Cues.Span> spans, List<Block> blocks, int blockIndex, int timeMs) {
        if (spans == null || blocks == null || blockIndex < 0 || blockIndex >= blocks.size()) {
            return NONE;
        }
        Block block = blocks.get(blockIndex);
        int to = blockIndex + 1 < blocks.size()
                ? blocks.get(blockIndex + 1).spanIndex : spans.size();
        StringBuilder out = new StringBuilder();
        List<int[]> words = new ArrayList<>();
        for (int i = block.spanIndex; i < to && i < spans.size(); i++) {
            Cues.Span span = spans.get(i);
            String cue = span.text == null ? "" : span.text.trim();
            if (cue.isEmpty()) {
                continue;
            }
            int cueStart;
            if (out.length() == 0) {
                out.append(capitalize(cue));
                cueStart = 0;
            } else {
                boolean cjk = endsCjk(out) && startsCjk(cue);
                if (!cjk && !endsSentence(out) && startsSentence(cue)) {
                    out.append('.');
                }
                if (!cjk) {
                    out.append(' ');
                }
                cueStart = out.length();
                out.append(cue);
            }
            words.addAll(wordsIn(out, cueStart, out.length(), span.t0, Math.max(span.t1, span.t0 + 1)));
        }
        return pick(words, timeMs);
    }

    private static final int[] NONE = {-1, -1};

    /** Words in [from, to). Each entry is start, end, t0, t1. */
    private static List<int[]> wordsIn(CharSequence text, int from, int to, int t0, int t1) {
        List<int[]> local = new ArrayList<>();
        int i = from;
        while (i < to) {
            int cp = Character.codePointAt(text, i);
            if (Character.isWhitespace(cp)) {
                i += Character.charCount(cp);
                continue;
            }
            int start = i;
            if (isCjk(cp)) {
                i += Character.charCount(cp);
            } else {
                while (i < to) {
                    int next = Character.codePointAt(text, i);
                    if (Character.isWhitespace(next) || isCjk(next)) {
                        break;
                    }
                    i += Character.charCount(next);
                }
            }
            local.add(new int[] {start, i, Math.max(i - start, 1)});
        }
        List<int[]> out = new ArrayList<>();
        int total = 0;
        for (int[] word : local) {
            total += word[2];
        }
        if (total == 0) {
            return out;
        }
        int cursor = t0;
        int consumed = 0;
        for (int w = 0; w < local.size(); w++) {
            consumed += local.get(w)[2];
            int endTime = w == local.size() - 1
                    ? t1 : t0 + (int) ((long) (t1 - t0) * consumed / total);
            out.add(new int[] {local.get(w)[0], local.get(w)[1], cursor, endTime});
            cursor = endTime;
        }
        return out;
    }

    /** The word that contains timeMs, or the latest word already started. */
    private static int[] pick(List<int[]> words, int timeMs) {
        int[] hold = null;
        for (int[] word : words) {
            if (timeMs < word[2]) {
                break;
            }
            hold = word;
            if (timeMs < word[3]) {
                return new int[] {word[0], word[1]};
            }
        }
        if (hold == null) {
            return NONE;
        }
        return new int[] {hold[0], hold[1]};
    }

    static String join(List<String> cues) {
        StringBuilder out = new StringBuilder();
        for (String raw : cues) {
            String cue = raw == null ? "" : raw.trim();
            if (cue.isEmpty()) {
                continue;
            }
            if (out.length() == 0) {
                out.append(capitalize(cue));
                continue;
            }
            boolean cjk = endsCjk(out) && startsCjk(cue);
            if (!cjk && !endsSentence(out) && startsSentence(cue)) {
                out.append('.');
            }
            if (!cjk) {
                out.append(' ');
            }
            out.append(cue);
        }
        if (out.length() == 0 || endsSentence(out) || bracketed(out) || cjkHeavy(out)) {
            return out.toString();
        }
        out.append('.');
        return out.toString();
    }

    private static String capitalize(String cue) {
        int cp = cue.codePointAt(0);
        if (!Character.isLowerCase(cp)) {
            return cue;
        }
        int count = Character.charCount(cp);
        return new String(Character.toChars(Character.toUpperCase(cp))) + cue.substring(count);
    }

    private static boolean startsSentence(String cue) {
        int cp = cue.codePointAt(0);
        return Character.isUpperCase(cp) && !allCaps(cue);
    }

    private static boolean allCaps(String text) {
        boolean letter = false;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isLetter(cp)) {
                continue;
            }
            letter = true;
            if (Character.isLowerCase(cp)) {
                return false;
            }
        }
        return letter;
    }

    private static boolean endsSentence(CharSequence text) {
        char c = last(text);
        return c == '.' || c == '?' || c == '!' || c == '…'
                || c == '。' || c == '！' || c == '？';
    }

    private static char last(CharSequence text) {
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'' || c == ')' || c == '”' || c == '’'
                    || Character.isWhitespace(c)) {
                continue;
            }
            return c;
        }
        return 0;
    }

    private static boolean bracketed(CharSequence text) {
        String trimmed = text.toString().trim();
        return trimmed.startsWith("[") && trimmed.endsWith("]");
    }

    private static boolean cjkHeavy(CharSequence text) {
        int cjk = 0;
        int latin = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = Character.codePointAt(text, i);
            i += Character.charCount(cp);
            if (isCjk(cp)) {
                cjk++;
            } else if (Character.isLetter(cp)) {
                latin++;
            }
        }
        return cjk > 0 && cjk >= latin;
    }

    private static boolean startsCjk(String text) {
        return isCjk(text.codePointAt(0));
    }

    private static boolean endsCjk(CharSequence text) {
        int cp = 0;
        for (int i = 0; i < text.length(); ) {
            cp = Character.codePointAt(text, i);
            i += Character.charCount(cp);
        }
        return isCjk(cp);
    }

    private static boolean isCjk(int cp) {
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}
