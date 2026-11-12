package app.cuepipe;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class RumbleTest {
    @Test
    public void aWatchLinkIsNotAnEmbedId() {
        assertTrue(Rumble.isWatch(
                "https://rumble.com/v180wvd-911-audio.html"));
        assertEquals("v15eqxl", Rumble.embedId("https://rumble.com/embed/v15eqxl/"));
        assertNull(Rumble.embedId("https://rumble.com/v180wvd-911-audio.html"));
    }

    @Test
    public void oembedPointsAtTheEmbedAndThePageKeepsEachHeight() {
        String json = "{\"title\":\"911 Audio\",\"author_name\":\"Mr Producer\","
                + "\"author_url\":\"https://rumble.com/c/RichSementa\","
                + "\"thumbnail_url\":\"https://hugh.cdn.rumble.cloud/t.jpg\","
                + "\"html\":\"<iframe src=\\\"https://rumble.com/embed/v15eqxl/\\\"></iframe>\"}";
        Rumble.Clip clip = Rumble.oembed(json);
        assertEquals("911 Audio", clip.title);
        assertEquals("https://rumble.com/embed/v15eqxl/", clip.embed);

        String html = "\"ua\":{\"mp4\":{"
                + "\"360\":{\"url\":\"https:\\/\\/hugh.cdn.rumble.cloud\\/a.mp4\"},"
                + "\"720\":{\"url\":\"https:\\/\\/hugh.cdn.rumble.cloud\\/b.mp4\"}}}";
        List<Rumble.Stream> streams = Rumble.streams(html);
        assertEquals(2, streams.size());
        assertEquals("https://hugh.cdn.rumble.cloud/b.mp4", Rumble.choose(streams, -1));
        assertEquals("https://hugh.cdn.rumble.cloud/a.mp4", Rumble.choose(streams, 0));
    }
}
