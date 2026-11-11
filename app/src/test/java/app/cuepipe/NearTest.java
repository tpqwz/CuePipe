package app.cuepipe;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

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
    public void blendKeepsACloseLineTheWordsMissed() {
        Shelf.Hit lexical = new Shelf.Hit(1, 2, 10, "title", "channel", "words");
        Shelf.Hit close = new Shelf.Hit(1, 3, 0, "title", "channel", "nearby idea");
        Shelf.Hit far = new Shelf.Hit(1, 4, 0, "title", "channel", "unrelated");
        List<Shelf.Hit> hits = Near.blend(
                Arrays.asList(lexical),
                Arrays.asList(new Near.Scored(close, 0.9f), new Near.Scored(far, 0.1f)));
        assertEquals(2, hits.size());
        assertEquals(10, hits.get(0).byteOffset);
        assertEquals(0, hits.get(1).byteOffset);
        assertEquals(3, hits.get(1).trackId);
    }
}
