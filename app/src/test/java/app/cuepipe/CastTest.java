package app.cuepipe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CastTest {
    @Test
    public void aFeedKeepsTheTranscriptAndSkipsABareEpisode() throws Exception {
        String xml = "<rss xmlns:podcast=\"https://podcastindex.org/namespace/1.0\"><channel>"
                + "<title>Night Show</title>"
                + "<item><title>One</title><link>https://example.test/one</link>"
                + "<enclosure url=\"https://example.test/one.mp3\" type=\"audio/mpeg\"/>"
                + "<podcast:transcript url=\"https://example.test/one.vtt\" type=\"text/vtt\" language=\"en\"/>"
                + "</item>"
                + "<item><title>No file</title></item>"
                + "<item><title>Two</title><enclosure url=\"https://example.test/two.mp3\"/></item>"
                + "</channel></rss>";
        Cast.Show show = Cast.parse(xml);
        assertEquals("Night Show", show.title);
        assertEquals(2, show.episodes.size());
        assertEquals("One", show.episodes.get(0).title);
        assertEquals("https://example.test/one", show.episodes.get(0).page);
        assertEquals("https://example.test/one.mp3", show.episodes.get(0).audio);
        assertEquals(1, show.episodes.get(0).captions.size());
        assertEquals("en", show.episodes.get(0).captions.get(0).lang);
        assertTrue(show.episodes.get(1).captions.isEmpty());
    }
}
