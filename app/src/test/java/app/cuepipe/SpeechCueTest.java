package app.cuepipe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class SpeechCueTest {
    @Test
    public void segmentLineBecomesASpan() {
        Cues.Transcript transcript = Speech.cues("0\t1500\thello there\n");
        assertEquals("asr", transcript.source);
        assertEquals(1, transcript.spans.size());
        assertEquals(0, transcript.spans.get(0).t0);
        assertEquals(1500, transcript.spans.get(0).t1);
        assertTrue(transcript.body.contains("hello there"));
    }

    @Test
    public void languageLineIsNotACue() {
        String raw = "lang\ten\n0\t1500\thello there\n";
        assertEquals("en", Speech.languageOf(raw));
        Cues.Transcript transcript = Speech.cues(raw);
        assertEquals(1, transcript.spans.size());
        assertEquals("hello there", transcript.spans.get(0).text);
    }

    @Test
    public void missingLanguageIsUndetermined() {
        assertEquals("und", Speech.languageOf("0\t10\thi\n"));
        assertEquals("und", Speech.languageOf(null));
    }
}
