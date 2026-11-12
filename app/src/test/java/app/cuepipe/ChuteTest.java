package app.cuepipe;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ChuteTest {
    @Test
    public void aWatchLinkIsTheVideoId() {
        assertEquals("QiM9dAQNxwPf",
                Chute.videoId("https://www.bitchute.com/video/QiM9dAQNxwPf/"));
        assertEquals("agDuiW1AhWxz",
                Chute.channelId("https://www.bitchute.com/channel/agDuiW1AhWxz/"));
        assertNull(Chute.videoId("https://www.bitchute.com/video/playlist?playlistId=abc"));
        assertNull(Chute.videoId("https://www.youtube.com/watch?v=abc"));
    }

    @Test
    public void aListKeepsTheChannelAndADetailKeepsTheStream() {
        String list = "{\"videos\":[{\"video_id\":\"fhJxf0HdsLPC\","
                + "\"video_name\":\"Look 30 @ 70\","
                + "\"video_url\":\"/video/fhJxf0HdsLPC/\","
                + "\"thumbnail_url\":\"https://static-3.bitchute.com/cover.jpg\","
                + "\"channel\":{\"channel_id\":\"agDuiW1AhWxz\",\"channel_name\":\"bluewater\","
                + "\"channel_url\":\"/channel/agDuiW1AhWxz/\"}}]}";
        List<Chute.Clip> clips = Chute.videos(list);
        assertEquals(1, clips.size());
        assertEquals("fhJxf0HdsLPC", clips.get(0).id);
        assertEquals("https://www.bitchute.com/video/fhJxf0HdsLPC/", clips.get(0).page);
        assertEquals("bluewater", clips.get(0).channel);
        assertEquals("https://www.bitchute.com/channel/agDuiW1AhWxz/", clips.get(0).channelPage);

        String detail = "{\"video_id\":\"QiM9dAQNxwPf\",\"video_name\":\"Say \\\"hi\\\"\","
                + "\"description\":\"<p>Hello</p>\","
                + "\"channel\":{\"channel_id\":\"c1\",\"channel_name\":\"BaggyPants\","
                + "\"channel_url\":\"/channel/c1/\"}}";
        Chute.Clip clip = Chute.video(detail);
        assertEquals("Say \"hi\"", clip.title);
        assertEquals("Hello", clip.description);
        assertEquals("https://seed.bitchute.com/k/QiM9dAQNxwPf.mp4",
                Chute.media("{\"media_url\":\"https://seed.bitchute.com/k/QiM9dAQNxwPf.mp4\"}"));
        assertTrue(Chute.searchBody("a\"b").contains("\"a\\\"b\""));
    }
}
