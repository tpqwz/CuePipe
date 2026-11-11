package app.cuepipe;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CuesTest {
    @Test
    public void vttKeepsTwoSeparateLines() {
        String vtt = "WEBVTT\n\n"
                + "00:00:01.000 --> 00:00:02.000\n"
                + "alpha\n\n"
                + "00:00:03.000 --> 00:00:04.500\n"
                + "beta gamma\n";
        Cues.Transcript t = Cues.parse(vtt, "vtt", false);
        assertEquals("manual", t.source);
        assertEquals(2, t.spans.size());
        assertEquals(1000, t.spans.get(0).t0);
        assertEquals(2000, t.spans.get(0).t1);
        assertEquals("alpha", t.spans.get(0).text);
        assertEquals("beta gamma", t.spans.get(1).text);
        assertTrue(t.body.indexOf('\n') > 0);
        assertEquals(0, t.spans.get(0).byteStart);
        assertEquals(t.spans.get(0).byteLen, "alpha".length());
    }

    @Test
    public void ttmlBeginEndInANamespace() {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>"
                + "<tt xmlns=\"http://www.w3.org/ns/ttml\">"
                + "<body><div>"
                + "<p begin=\"00:00:01.200\" end=\"00:00:03.360\">All right, so here we are<br />elephants</p>"
                + "<p begin=\"00:00:05.318\" end=\"00:00:07.974\">the cool thing</p>"
                + "</div></body></tt>";
        Cues.Transcript t = Cues.parse(xml, "ttml", false);
        assertEquals(2, t.spans.size());
        assertEquals(1200, t.spans.get(0).t0);
        assertEquals(3360, t.spans.get(0).t1);
        assertEquals("All right, so here we are elephants", t.spans.get(0).text);
        assertEquals(5318, t.spans.get(1).t0);
    }

    @Test
    public void rollingPrefixCollapsesAndKeepsTheEarlierStart() {
        String xml = "<timedtext><body>"
                + "<p t=\"0\" d=\"2000\">hello</p>"
                + "<p t=\"500\" d=\"2000\">hello world</p>"
                + "</body></timedtext>";
        Cues.Transcript t = Cues.parse(xml, "srv3", true);
        assertEquals("auto", t.source);
        assertEquals(1, t.spans.size());
        assertEquals("hello world", t.spans.get(0).text);
        assertEquals(0, t.spans.get(0).t0);
        assertEquals(2500, t.spans.get(0).t1);
    }

    @Test
    public void json3ReadsSegs() {
        String json = "{\"events\":["
                + "{\"tStartMs\":80,\"dDurationMs\":1000,\"segs\":[{\"utf8\":\"ask not\"}]},"
                + "{\"tStartMs\":4000,\"dDurationMs\":1200,\"segs\":["
                + "{\"utf8\":\"what \"},{\"utf8\":\"you can do\"}]}"
                + "]}";
        Cues.Transcript t = Cues.parse(json, null, true);
        assertEquals(2, t.spans.size());
        assertEquals("ask not", t.spans.get(0).text);
        assertEquals(80, t.spans.get(0).t0);
        assertEquals("what you can do", t.spans.get(1).text);
        assertEquals(4000, t.spans.get(1).t0);
    }

    @Test
    public void srtClockAndIndex() {
        String srt = "1\n00:00:01,500 --> 00:00:02,000\nLand mines\n\n"
                + "2\n00:01:00,000 --> 00:01:01,000\nSecond line\n";
        Cues.Transcript t = Cues.parse(srt, "srt", false);
        assertEquals(2, t.spans.size());
        assertEquals(1500, t.spans.get(0).t0);
        assertEquals("Land mines", t.spans.get(0).text);
        assertEquals(60_000, t.spans.get(1).t0);
    }

    @Test
    public void secondTrackAlignsOnOverlap() {
        String en = "WEBVTT\n\n00:00:00.000 --> 00:00:02.000\nhello\n\n"
                + "00:00:05.000 --> 00:00:07.000\nworld\n";
        String ja = "WEBVTT\n\n00:00:00.400 --> 00:00:01.800\nこんにちは\n\n"
                + "00:00:09.000 --> 00:00:10.000\n別\n";
        Cues.Transcript a = Cues.parse(en, "vtt", false);
        Cues.Transcript b = Cues.parse(ja, "vtt", false);
        assertEquals(0, Cues.overlapping(b.spans, a.spans.get(0).t0, a.spans.get(0).t1));
        assertEquals(-1, Cues.overlapping(b.spans, a.spans.get(1).t0, a.spans.get(1).t1));
    }

    @Test
    public void captionAtFollowsThePrimaryLine() {
        String en = "WEBVTT\n\n00:00:00.000 --> 00:00:02.000\nhello\n\n"
                + "00:00:05.000 --> 00:00:07.000\nworld\n";
        String ja = "WEBVTT\n\n00:00:00.400 --> 00:00:01.800\nこんにちは\n";
        Cues.Transcript a = Cues.parse(en, "vtt", false);
        Cues.Transcript b = Cues.parse(ja, "vtt", false);
        assertEquals("hello\nこんにちは", Cues.captionAt(a.spans, List.of(b.spans), 1000));
        assertEquals("", Cues.captionAt(a.spans, List.of(b.spans), 3000));
        assertEquals("world", Cues.captionAt(a.spans, List.of(b.spans), 5000));
        assertEquals(0, Cues.indexAt(a.spans, 1000));
        assertEquals(0, Cues.indexAt(a.spans, 3000));
        assertEquals(1, Cues.indexAt(a.spans, 5000));
        assertEquals(-1, Cues.indexAt(a.spans, -1));
        assertEquals(-1, Cues.indexAt(List.of(), 1000));
    }

    @Test
    public void offsetLandsInTheSecondSpan() {
        Cues.Transcript t = Cues.parse(
                "WEBVTT\n\n00:00:00.000 --> 00:00:01.000\nalpha\n\n"
                        + "00:00:02.000 --> 00:00:03.000\nbeta\n",
                "vtt", false);
        int nl = t.body.indexOf('\n');
        assertEquals(1, Cues.spanIndex(t.spans, nl + 1));
        assertEquals(0, Cues.spanIndex(t.spans, 0));
        assertEquals(-1, Cues.spanIndex(t.spans, -1));
        assertEquals(nl + 1, Cues.firstOffset("0 0 " + (nl + 1) + " 4"));
    }

    @Test
    public void utf8OffsetCountsBytes() {
        String vtt = "WEBVTT\n\n00:00:00.000 --> 00:00:01.000\n東京\n";
        Cues.Transcript t = Cues.parse(vtt, "vtt", false);
        assertEquals(6, t.spans.get(0).byteLen);
    }

    @Test
    public void translatedLinesKeepTheTimes() {
        List<Cues.Span> spans = Arrays.asList(new Cues.Span(0, 1500, 0, 5, "hello"));
        Cues.Transcript out = Cues.withLines(spans, Arrays.asList("hola"), "translation");
        assertEquals("translation", out.source);
        assertEquals(0, out.spans.get(0).t0);
        assertEquals(1500, out.spans.get(0).t1);
        assertEquals("hola", out.spans.get(0).text);
    }
}
