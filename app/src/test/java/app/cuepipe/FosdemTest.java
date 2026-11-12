package app.cuepipe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class FosdemTest {
    @Test
    public void aScheduleLinkAndAFileLinkNameTheTalk() {
        assertEquals("2026", Fosdem.key(
                "https://fosdem.org/2026/schedule/event/XN9DAW-closing_fosdem_2026/")[0]);
        assertEquals("XN9DAW-closing_fosdem_2026", Fosdem.key(
                "https://video.fosdem.org/2026/janson/XN9DAW-closing_fosdem_2026.mp4")[1]);
        assertNull(Fosdem.key("https://www.youtube.com/watch?v=abc"));
    }

    @Test
    public void aTalkKeepsTheMp4AndTheCaption() {
        String json = "{\"items\":[{\"title\":\"Closing\",\"year\":2026,\"slug\":\"XN9DAW\","
                + "\"url\":\"https://fosdem.org/2026/schedule/event/XN9DAW/\","
                + "\"thumbnailUrl\":\"https://fosdem-thumbnails.opensource-tv.com/a.jpg\","
                + "\"mp4Url\":\"https://ftp.belnet.be/mirror/FOSDEM/video/a.mp4\","
                + "\"speakers\":[{\"name\":\"Johan\"}],"
                + "\"subtitles\":[{\"language\":\"en\",\"label\":\"English\","
                + "\"url\":\"https://video.fosdem.org/a.vtt\"}]}]}";
        Fosdem.Talk talk = Fosdem.talks(json).get(0);
        assertEquals("Closing", talk.title);
        assertEquals("Johan", talk.speaker);
        assertEquals("https://ftp.belnet.be/mirror/FOSDEM/video/a.mp4", talk.media);
        assertEquals("en", talk.captions.get(0).lang);
        assertEquals("https://video.fosdem.org/a.vtt", talk.captions.get(0).url);
    }
}
