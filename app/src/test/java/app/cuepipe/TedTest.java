package app.cuepipe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class TedTest {
    @Test
    public void aTalkLinkIsTheSlug() {
        assertEquals("ken_robinson_says_schools_kill_creativity",
                Ted.slug("https://www.ted.com/talks/ken_robinson_says_schools_kill_creativity"));
        assertNull(Ted.slug("https://www.youtube.com/watch?v=abc"));
    }

    @Test
    public void aTalkKeepsTheHighFileAndTheCaption() {
        String details = "{\"data\":{\"videos\":{\"nodes\":[{"
                + "\"title\":\"Hello\",\"slug\":\"hello_talk\","
                + "\"canonicalUrl\":\"https://www.ted.com/talks/hello_talk\","
                + "\"audioDownload\":\"https://download.ted.com/a.mp3\","
                + "\"nativeDownloads\":{\"low\":\"https://download.ted.com/l.mp4\","
                + "\"medium\":\"https://download.ted.com/m.mp4\",\"high\":\"https://download.ted.com/h.mp4\"},"
                + "\"playerData\":\"{\\\"resources\\\":{\\\"hls\\\":{\\\"metadata\\\":\\\"https://hls.ted.com/m.json\\\"}}}\","
                + "\"speakers\":{\"nodes\":[{\"firstname\":\"Ken\",\"lastname\":\"Robinson\",\"photoUrl\":\"https://ted.com/k.jpg\"}]}"
                + "}]}}}";
        Ted.Talk talk = Ted.details(details);
        assertEquals("Ken Robinson", talk.speaker);
        assertEquals("https://hls.ted.com/m.json", talk.metadata);
        assertEquals("https://download.ted.com/h.mp4", Ted.choose(talk, -1));
        assertEquals("https://download.ted.com/m.mp4", Ted.choose(talk, 720));
        assertEquals("https://download.ted.com/a.mp3", Ted.choose(talk, 0));

        String meta = "{\"subtitles\":[{\"code\":\"en\",\"name\":\"English\","
                + "\"webvtt\":\"https://hls.ted.com/en.vtt\"}]}";
        assertEquals("https://hls.ted.com/en.vtt", Ted.captions(meta).get(0).url);
    }
}
