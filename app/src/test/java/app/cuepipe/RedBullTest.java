package app.cuepipe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class RedBullTest {
    @Test
    public void aFeedKeepsFilmsAndThePlayerKeepsEnglishCaptions() {
        String feed = "{\"data\":["
                + "{\"id\":\"rrn:content:shows:abc\",\"content\":{\"title\":\"Skip\"}},"
                + "{\"id\":\"rrn:content:films:9d56:en-INT\",\"content\":{\"title\":\"Backbone\"},"
                + "\"media\":{\"mainImage\":{\"imageURL\":\"https://img.redbull.com/{op}/a.jpg\"}}}"
                + "]}";
        RedBull.Clip clip = RedBull.feed(feed).get(0);
        assertEquals("Backbone", clip.title);
        assertTrue(clip.thumb.contains("c_fill"));
        assertEquals("rrn:content:films:9d56:en-INT", RedBull.id(clip.page));

        String player = "{\"videoId\":\"rrn:content:films:9d56:en-INT\","
                + "\"videoUrl\":\"https://play.redbull.com/a.m3u8\",\"assetId\":\"ASSET\","
                + "\"availableSubtitleLanguages\":{\"en-US\":\"en\",\"de-DE\":\"de\"}}";
        RedBull.Clip play = RedBull.player(player);
        assertEquals("https://play.redbull.com/a.m3u8", play.media);
        assertEquals(1, play.captions.size());
        assertTrue(play.captions.get(0).url.contains("/ASSET/vod/fmp4/en-US/"));
    }
}
