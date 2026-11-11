package app.cuepipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Paragraphs and the blend of a word hit with a vector score.
 * The model that produces the vector is not loaded here.
 */
public final class Near {
    private static final int PARAGRAPH = 480;

    private Near() {
    }

    /** One stored vector. byteOffset is the first span, which is where a hit seeks. */
    public static final class Paragraph {
        public final int spanIndex;
        public final int byteOffset;
        public final String text;

        public Paragraph(int spanIndex, int byteOffset, String text) {
            this.spanIndex = spanIndex;
            this.byteOffset = byteOffset;
            this.text = text;
        }
    }

    public static final class Scored {
        public final Shelf.Hit hit;
        public final float score;

        public Scored(Shelf.Hit hit, float score) {
            this.hit = hit;
            this.score = score;
        }
    }

    /** WordPiece vocabulary. Ids are line numbers in a BERT vocab file. */
    public static final class Vocab {
        final Map<String, Integer> ids;
        final int cls;
        final int sep;
        final int unk;
        final int pad;

        Vocab(Map<String, Integer> ids, int cls, int sep, int unk, int pad) {
            this.ids = ids;
            this.cls = cls;
            this.sep = sep;
            this.unk = unk;
            this.pad = pad;
        }
    }

    public static List<Paragraph> paragraphs(List<Cues.Span> spans) {
        List<Paragraph> out = new ArrayList<>();
        if (spans == null) {
            return out;
        }
        StringBuilder text = new StringBuilder();
        int start = -1;
        int offset = 0;
        for (int i = 0; i < spans.size(); i++) {
            Cues.Span span = spans.get(i);
            String piece = span.text == null ? "" : span.text.trim();
            if (piece.isEmpty()) {
                continue;
            }
            if (start >= 0 && text.length() + 1 + piece.length() > PARAGRAPH) {
                out.add(new Paragraph(start, offset, text.toString()));
                text.setLength(0);
                start = -1;
            }
            if (start < 0) {
                start = i;
                offset = span.byteStart;
            }
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(piece);
        }
        if (start >= 0 && text.length() > 0) {
            out.add(new Paragraph(start, offset, text.toString()));
        }
        return out;
    }

    public static Vocab vocab(List<String> lines) {
        Map<String, Integer> ids = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null) {
                continue;
            }
            String token = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            if (!token.isEmpty()) {
                ids.put(token, i);
            }
        }
        return new Vocab(
                ids,
                ids.getOrDefault("[CLS]", 101),
                ids.getOrDefault("[SEP]", 102),
                ids.getOrDefault("[UNK]", 100),
                ids.getOrDefault("[PAD]", 0));
    }

    /** BERT tokens, capped at maxLen, wrapped with [CLS] and [SEP]. */
    public static int[] encode(Vocab vocab, String text, int maxLen) {
        List<Integer> ids = new ArrayList<>();
        ids.add(vocab.cls);
        int room = Math.max(maxLen, 2);
        for (String token : basic(text == null ? "" : text)) {
            for (int id : piece(vocab, token)) {
                if (ids.size() + 1 >= room) {
                    break;
                }
                ids.add(id);
            }
            if (ids.size() + 1 >= room) {
                break;
            }
        }
        ids.add(vocab.sep);
        int[] out = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            out[i] = ids.get(i);
        }
        return out;
    }

    static List<String> basic(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < lower.length(); ) {
            int cp = lower.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp)) {
                flush(word, out);
            } else if (Character.isLetterOrDigit(cp)) {
                word.appendCodePoint(cp);
            } else {
                flush(word, out);
                out.add(new String(Character.toChars(cp)));
            }
        }
        flush(word, out);
        return out;
    }

    private static void flush(StringBuilder word, List<String> out) {
        if (word.length() > 0) {
            out.add(word.toString());
            word.setLength(0);
        }
    }

    private static List<Integer> piece(Vocab vocab, String word) {
        List<Integer> ids = new ArrayList<>();
        int start = 0;
        while (start < word.length()) {
            int end = word.length();
            Integer id = null;
            while (start < end) {
                String sub = word.substring(start, end);
                if (start > 0) {
                    sub = "##" + sub;
                }
                id = vocab.ids.get(sub);
                if (id != null) {
                    break;
                }
                end--;
            }
            if (id == null) {
                ids.clear();
                ids.add(vocab.unk);
                return ids;
            }
            ids.add(id);
            start = end;
        }
        if (ids.isEmpty()) {
            ids.add(vocab.unk);
        }
        return ids;
    }

    public static float cosine(float[] left, float[] right) {
        if (left == null || right == null) {
            return 0f;
        }
        int length = Math.min(left.length, right.length);
        float sum = 0f;
        for (int i = 0; i < length; i++) {
            sum += left[i] * right[i];
        }
        return sum;
    }

    public static float[] unit(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += (double) value * value;
        }
        double norm = Math.sqrt(sum);
        float[] out = new float[vector.length];
        if (norm == 0) {
            return out;
        }
        for (int i = 0; i < vector.length; i++) {
            out[i] = (float) (vector[i] / norm);
        }
        return out;
    }

    /**
     * Lexical rank and vector cosine. A vector-only line stays when it is close.
     * A weak neighbor that the words did not hit is left out.
     */
    public static List<Shelf.Hit> blend(List<Shelf.Hit> lexical, List<Scored> meaning) {
        Map<String, Shelf.Hit> byKey = new LinkedHashMap<>();
        Map<String, Float> weight = new HashMap<>();
        int n = lexical == null ? 0 : lexical.size();
        for (int i = 0; i < n; i++) {
            Shelf.Hit hit = lexical.get(i);
            String key = key(hit);
            weight.put(key, (n - i) / (float) n);
            byKey.put(key, hit);
        }
        Map<String, Float> near = new HashMap<>();
        if (meaning != null) {
            for (Scored scored : meaning) {
                String key = key(scored.hit);
                float value = Math.max(0f, scored.score);
                Float have = near.get(key);
                if (have == null || value > have) {
                    near.put(key, value);
                    if (!byKey.containsKey(key)) {
                        byKey.put(key, scored.hit);
                    }
                }
            }
        }
        List<String> keys = new ArrayList<>(weight.keySet());
        for (String key : near.keySet()) {
            if (!weight.containsKey(key)) {
                keys.add(key);
            }
        }
        List<Ranked> ranked = new ArrayList<>();
        for (String key : keys) {
            float lex = weight.containsKey(key) ? weight.get(key) : 0f;
            float sem = near.containsKey(key) ? near.get(key) : 0f;
            float score = 0.55f * lex + 0.45f * sem;
            if (score <= 0.2f && !weight.containsKey(key)) {
                continue;
            }
            ranked.add(new Ranked(key, score));
        }
        ranked.sort((a, b) -> Float.compare(b.score, a.score));
        List<Shelf.Hit> out = new ArrayList<>();
        for (Ranked row : ranked) {
            out.add(byKey.get(row.key));
        }
        return out;
    }

    private static final class Ranked {
        final String key;
        final float score;

        Ranked(String key, float score) {
            this.key = key;
            this.score = score;
        }
    }

    private static String key(Shelf.Hit hit) {
        return hit.trackId + ":" + hit.byteOffset;
    }
}
