package app.cuepipe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class NasaTest {
    @Test
    public void aVideoLinkIsTheSlug() {
        assertEquals("moon-base-update-lunar-rovers",
                Nasa.slug("https://plus.nasa.gov/video/moon-base-update-lunar-rovers/"));
        assertNull(Nasa.slug("https://www.nasa.gov/"));
    }

    @Test
    public void aVideoKeepsTheHlsUrlAndDecodesTheTitle() {
        String json = "[{\"slug\":\"moon\",\"link\":\"https://plus.nasa.gov/video/moon/\","
                + "\"title\":{\"rendered\":\"Moon &#8211; Base\"},"
                + "\"meta\":{\"video-url\":\"https://nasaplus.akamaized.net/output/1.m3u8\","
                + "\"featured-image\":\"https://plus.nasa.gov/a.jpg\"}}]";
        Nasa.Clip clip = Nasa.videos(json).get(0);
        assertEquals("Moon – Base", clip.title);
        assertEquals("https://nasaplus.akamaized.net/output/1.m3u8", clip.media);
        assertEquals("https://plus.nasa.gov/a.jpg", clip.thumb);
    }
}
