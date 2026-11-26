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
                + "<podcast:chapters url=\"https://example.test/one.json\" type=\"application/json\"/>"
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
        assertEquals("https://example.test/one.json", show.episodes.get(0).chapters);
        assertTrue(show.episodes.get(1).captions.isEmpty());
        assertEquals("", show.episodes.get(1).chapters);
        String alt = "<rss><channel><item><title>Alt</title>"
                + "<alternateEnclosure type=\"audio/mpeg\">"
                + "<source uri=\"https://example.test/alt.mp3\"/>"
                + "</alternateEnclosure></item></channel></rss>";
        assertEquals("https://example.test/alt.mp3", Cast.parse(alt).episodes.get(0).audio);
        StringBuilder many = new StringBuilder("<rss><channel>");
        for (int i = 0; i < Cast.PAGE + 1; i++) {
            many.append("<item><title>E").append(i).append("</title><enclosure url=\"https://example.test/")
                    .append(i).append(".mp3\"/></item>");
        }
        many.append("</channel></rss>");
        assertEquals(Cast.PAGE + 1, Cast.parse(many.toString()).episodes.size());
        try {
            Cast.parse("<!DOCTYPE rss [<!ENTITY x \"a\">]><rss><channel></channel></rss>");
            throw new AssertionError("doctype");
        } catch (IllegalArgumentException expected) {
            assertEquals("This feed is not a podcast", expected.getMessage());
        }
        String blog = "<rss><channel><title>Notes</title>"
                + "<item><title>One</title><link>https://example.test/one</link></item>"
                + "<item><title></title><link>https://example.test/two</link></item>"
                + "</channel></rss>";
        java.util.List<Cast.Post> posts = Cast.posts(blog);
        assertEquals(2, posts.size());
        assertEquals("One", posts.get(0).title);
        assertEquals("https://example.test/one", posts.get(0).url);
        String note = Cast.opml(java.util.Arrays.asList(
                new String[] {"Podcast", "Night & Show", "https://example.test/feed.xml"},
                new String[] {"RSS", "Notes", "https://example.test/blog.xml"}));
        java.util.List<String[]> back = Cast.outlines(note);
        assertEquals(2, back.size());
        assertEquals("Podcast", back.get(0)[0]);
        assertEquals("Night & Show", back.get(0)[1]);
        assertEquals("https://example.test/feed.xml", back.get(0)[2]);
        assertEquals("RSS", back.get(1)[0]);
    }
}
