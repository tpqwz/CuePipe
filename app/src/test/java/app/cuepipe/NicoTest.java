package app.cuepipe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class NicoTest {
    @Test
    public void aWatchLinkIsTheVideoAndAUserLinkIsTheChannel() {
        assertEquals("sm47034387", Nico.videoId("https://www.nicovideo.jp/watch/sm47034387"));
        assertEquals("21606835", Nico.userId("https://www.nicovideo.jp/user/21606835/video"));
        assertNull(Nico.videoId("https://www.nicovideo.jp/watch/lv123456789"));
        assertNull(Nico.userId("https://www.nicovideo.jp/watch/sm9"));
    }

    @Test
    public void aListAndAWatchPageKeepThePlayablePair() {
        String list = "{\"data\":{\"items\":[{\"essential\":{"
                + "\"id\":\"sm9\",\"title\":\"Lets go\","
                + "\"thumbnail\":{\"listingUrl\":\"https://example.test/t.jpg\"},"
                + "\"owner\":{\"id\":\"7\",\"name\":\"Nico\"}}}]}}";
        assertEquals("sm9", Nico.videos(list).get(0).id);
        assertEquals("https://www.nicovideo.jp/user/7", Nico.videos(list).get(0).channelPage);

        String html = "<meta name=\"server-response\" content=\""
                + "{&quot;data&quot;:{&quot;response&quot;:{&quot;$watchV4&quot;:{&quot;data&quot;:{"
                + "&quot;client&quot;:{&quot;watchTrackId&quot;:&quot;track1&quot;},"
                + "&quot;media&quot;:{&quot;accessRightKey&quot;:&quot;key1&quot;,&quot;contents&quot;:{"
                + "&quot;videos&quot;:["
                + "{&quot;id&quot;:&quot;video-h264-1080p&quot;,&quot;isAvailable&quot;:false,&quot;height&quot;:1080},"
                + "{&quot;id&quot;:&quot;video-h264-360p&quot;,&quot;isAvailable&quot;:true,&quot;height&quot;:360}"
                + "],&quot;audios&quot;:[{&quot;id&quot;:&quot;audio-aac-64kbps&quot;,&quot;isAvailable&quot;:true}]"
                + "}},&quot;video&quot;:{&quot;id&quot;:&quot;sm9&quot;,&quot;title&quot;:&quot;Lets go&quot;,"
                + "&quot;description&quot;:&quot;&lt;p&gt;Hi&lt;/p&gt;&quot;,"
                + "&quot;thumbnail&quot;:{&quot;normal&quot;:&quot;https://example.test/n.jpg&quot;}},"
                + "&quot;metadata&quot;:{&quot;jsonLd&quot;:{&quot;owner&quot;:{&quot;id&quot;:&quot;7&quot;,"
                + "&quot;name&quot;:&quot;Nico&quot;}}}"
                + "}}}}}\" >";
        Nico.Watch watch = Nico.watch(html);
        assertEquals("sm9", watch.clip.id);
        assertEquals("Hi", watch.clip.description);
        assertEquals("key1", watch.accessKey);
        assertEquals("track1", watch.trackId);
        String[] pair = Nico.pair(watch, 720);
        assertEquals("video-h264-360p", pair[0]);
        assertEquals("audio-aac-64kbps", pair[1]);
        assertEquals("https://delivery.example/master.m3u8",
                Nico.contentUrl("{\"data\":{\"contentUrl\":\"https://delivery.example/master.m3u8\"}}"));
        assertEquals("nicosid=abc", Nico.cookie("other=1; nicosid=abc; Path=/"));
    }
}
