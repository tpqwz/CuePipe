package app.cuepipe;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class KickTest {
    @Test
    public void aChannelLinkIsTheSlug() {
        assertEquals("xqc", Kick.slug("https://kick.com/xqc"));
        assertEquals("xqc", Kick.slug("https://kick.com/xqc/"));
        assertNull(Kick.slug("https://kick.com/xqc/videos/abc"));
        assertNull(Kick.slug("https://www.youtube.com/watch?v=abc"));
    }

    @Test
    public void aLiveListAndAChannelKeepThePlaybackUrl() {
        String list = "{\"data\":{\"livestreams\":[{\"title\":\"Night\",\"thumbnail\":{\"src\":\"https://images.kick.com/t.webp\"},"
                + "\"channel\":{\"slug\":\"xizyna\",\"username\":\"Xizyna\"}}]}}";
        List<Kick.Clip> clips = Kick.livestreams(list);
        assertEquals(1, clips.size());
        assertEquals("https://kick.com/xizyna", clips.get(0).page);
        assertEquals("Xizyna", clips.get(0).author);

        Kick.Clip channel = Kick.channel("{\"slug\":\"xizyna\",\"playback_url\":\"https://example.test/live.m3u8\","
                + "\"user\":{\"username\":\"Xizyna\"},\"livestream\":{\"session_title\":\"Night\"}}");
        assertEquals("Night", channel.title);
        assertEquals("https://example.test/live.m3u8", channel.playback);
    }
}
