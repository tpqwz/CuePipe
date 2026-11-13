package app.cuepipe;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public final class ProseTest {
    @Test
    public void aSentenceGainsAPeriodAndACapital() {
        List<Cues.Span> spans = Arrays.asList(
                new Cues.Span(0, 800, 0, 1, "the cat sat"),
                new Cues.Span(800, 1600, 1, 1, "on the mat"));
        List<Prose.Block> blocks = Prose.blocks(spans);
        assertEquals(1, blocks.size());
        assertEquals(0, blocks.get(0).t0);
        assertEquals("The cat sat on the mat.", blocks.get(0).text);
    }

    @Test
    public void anUppercaseCueStartsANewSentence() {
        List<Cues.Span> spans = Arrays.asList(
                new Cues.Span(0, 800, 0, 1, "the cat sat"),
                new Cues.Span(800, 1600, 1, 1, "Dogs run"));
        assertEquals("The cat sat. Dogs run.", Prose.blocks(spans).get(0).text);
    }

    @Test
    public void aPauseStartsAnotherParagraph() {
        List<Cues.Span> spans = Arrays.asList(
                new Cues.Span(0, 800, 0, 1, "hello there"),
                new Cues.Span(4000, 4800, 1, 1, "next part"));
        List<Prose.Block> blocks = Prose.blocks(spans);
        assertEquals(2, blocks.size());
        assertEquals(0, blocks.get(0).t0);
        assertEquals(4000, blocks.get(1).t0);
        assertEquals(1, blocks.get(1).spanIndex);
        assertEquals(1, Prose.blockAt(blocks, 1));
        assertEquals(0, Prose.blockAt(blocks, 0));
    }

    @Test
    public void chineseCuesAreNotGivenALatinPeriod() {
        List<Cues.Span> spans = Arrays.asList(
                new Cues.Span(0, 800, 0, 1, "你好"),
                new Cues.Span(800, 1600, 1, 1, "世界"));
        assertEquals("你好世界", Prose.blocks(spans).get(0).text);
    }

    @Test
    public void thePlayheadMarksOneWordInsideTheLine() {
        String text = "the cat sat";
        assertEquals("the", text.substring(Prose.wordAt(text, 0, 900, 0)[0], Prose.wordAt(text, 0, 900, 0)[1]));
        int[] mid = Prose.wordAt(text, 0, 900, 450);
        assertEquals("cat", text.substring(mid[0], mid[1]));
        int[] last = Prose.wordAt(text, 0, 900, 800);
        assertEquals("sat", text.substring(last[0], last[1]));
        assertEquals(-1, Prose.wordAt(text, 100, 200, 0)[0]);
    }

    @Test
    public void aParagraphMarksTheWordAndKeepsItAcrossTheGap() {
        List<Cues.Span> spans = Arrays.asList(
                new Cues.Span(0, 600, 0, 1, "the cat sat"),
                new Cues.Span(1000, 1600, 1, 1, "on the mat"));
        List<Prose.Block> blocks = Prose.blocks(spans);
        String text = blocks.get(0).text;
        int[] first = Prose.wordAt(spans, blocks, 0, 0);
        assertEquals("The", text.substring(first[0], first[1]));
        int[] held = Prose.wordAt(spans, blocks, 0, 800);
        assertEquals("sat", text.substring(held[0], held[1]));
        int[] next = Prose.wordAt(spans, blocks, 0, 1000);
        assertEquals("on", text.substring(next[0], next[1]));
    }

    @Test
    public void chineseMarksOneCharacter() {
        String text = "你好";
        int[] first = Prose.wordAt(text, 0, 1000, 0);
        assertEquals("你", text.substring(first[0], first[1]));
        int[] second = Prose.wordAt(text, 0, 1000, 500);
        assertEquals("好", text.substring(second[0], second[1]));
        List<Cues.Span> spans = Arrays.asList(
                new Cues.Span(0, 800, 0, 1, "你好"),
                new Cues.Span(800, 1600, 1, 1, "世界"));
        List<Prose.Block> blocks = Prose.blocks(spans);
        int[] joined = Prose.wordAt(spans, blocks, 0, 800);
        assertEquals("世", blocks.get(0).text.substring(joined[0], joined[1]));
    }

    @Test
    public void theOtherLanguageSitsUnderTheSameParagraph() {
        List<Cues.Span> spanish = Arrays.asList(
                new Cues.Span(0, 800, 0, 1, "el gato"),
                new Cues.Span(800, 1600, 1, 1, "se sentó"));
        assertEquals("El gato se sentó.", Prose.nested(spanish, 0, 1600));
        assertEquals("", Prose.nested(spanish, 3000, 4000));
    }
}
