package app.cuepipe;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public final class NearTest {
    @Test
    public void shortCuesShareAParagraph() {
        List<Cues.Span> spans = Arrays.asList(
                new Cues.Span(0, 1000, 0, 5, "hello"),
                new Cues.Span(1000, 2000, 6, 5, "there"));
        List<Near.Paragraph> paragraphs = Near.paragraphs(spans);
        assertEquals(1, paragraphs.size());
        assertEquals(0, paragraphs.get(0).spanIndex);
        assertEquals(0, paragraphs.get(0).byteOffset);
        assertEquals("hello there", paragraphs.get(0).text);
    }

    @Test
    public void aLongCueStartsANewParagraph() {
        StringBuilder longLine = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            longLine.append('a');
        }
        List<Cues.Span> spans = Arrays.asList(
                new Cues.Span(0, 1, 0, 4, "keep"),
                new Cues.Span(1, 2, 10, 500, longLine.toString()));
        List<Near.Paragraph> paragraphs = Near.paragraphs(spans);
        assertEquals(2, paragraphs.size());
        assertEquals(10, paragraphs.get(1).byteOffset);
    }

    @Test
    public void wordPieceWrapsClsAndSep() {
        Near.Vocab vocab = Near.vocab(Arrays.asList("[PAD]", "[UNK]", "[CLS]", "[SEP]", "hello", "##lo"));
        int[] ids = Near.encode(vocab, "hello", 16);
        assertEquals(2, ids[0]);
        assertEquals(4, ids[1]);
        assertEquals(3, ids[ids.length - 1]);
        int[] broken = Near.encode(vocab, "zzzz", 16);
        assertEquals(1, broken[1]);
    }

    @Test
    public void sentencePieceKeepsAWholeWordAndACharacter() {
        Map<String, Integer> ids = new HashMap<>();
        Map<String, Float> scores = new HashMap<>();
        put(ids, scores, "▁hello", 4, -0.1f);
        put(ids, scores, "▁world", 5, -0.2f);
        put(ids, scores, "▁", 6, -2f);
        put(ids, scores, "hello", 7, -1f);
        put(ids, scores, "world", 8, -1f);
        put(ids, scores, "你", 9, -0.3f);
        put(ids, scores, "好", 10, -0.3f);
        Near.Unigram model = new Near.Unigram(ids, scores, 3, 0, 2);
        int[] english = Near.encode(model, "hello world", 16);
        assertEquals(0, english[0]);
        assertEquals(4, english[1]);
        assertEquals(5, english[2]);
        assertEquals(2, english[3]);
        int[] chinese = Near.encode(model, "你好", 16);
        assertEquals(6, chinese[1]);
        assertEquals(9, chinese[2]);
        assertEquals(10, chinese[3]);
    }

    private static void put(Map<String, Integer> ids, Map<String, Float> scores,
                            String piece, int id, float score) {
        ids.put(piece, id);
        scores.put(piece, score);
    }

    @Test
    public void blendKeepsACloseLineTheWordsMissed() {
        Shelf.Hit lexical = new Shelf.Hit(1, 2, 10, "title", "channel", "words", 0);
        Shelf.Hit close = new Shelf.Hit(1, 3, 0, "title", "channel", "nearby idea", 0);
        Shelf.Hit far = new Shelf.Hit(1, 4, 0, "title", "channel", "unrelated", 0);
        List<Shelf.Hit> hits = Near.blend(
                Arrays.asList(lexical),
                Arrays.asList(new Near.Scored(close, 0.9f), new Near.Scored(far, 0.1f)));
        assertEquals(2, hits.size());
        assertEquals(10, hits.get(0).byteOffset);
        assertEquals(0, hits.get(1).byteOffset);
        assertEquals(3, hits.get(1).trackId);
    }
}
