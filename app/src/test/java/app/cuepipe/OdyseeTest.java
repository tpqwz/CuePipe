package app.cuepipe;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class OdyseeTest {
    @Test
    public void aWatchLinkBecomesAnLbryUri() {
        assertEquals("lbry://@Chan#ab/video#cd",
                Odysee.videoUri("https://odysee.com/@Chan:ab/video:cd"));
        assertEquals("ef", Odysee.channelId("https://odysee.com/@Chan:ef"));
        assertNull(Odysee.videoUri("https://odysee.com/@Chan:ef"));
        assertNull(Odysee.channelId("https://odysee.com/@Chan:ab/video:cd"));
    }

    @Test
    public void aFreeClaimKeepsTheV6FileAndAProtectedClaimDoesNot() {
        String list = "{\"result\":{\"items\":[{"
                + "\"name\":\"clip\",\"claim_id\":\"abcdef\","
                + "\"value\":{\"title\":\"Hello\",\"tags\":[\"news\"],"
                + "\"thumbnail\":{\"url\":\"https://thumbs.odycdn.com/a.jpg\"},"
                + "\"source\":{\"sd_hash\":\"0123456789abcdef\"}},"
                + "\"signing_channel\":{\"name\":\"@Chan\",\"claim_id\":\"99\"}}]}}";
        List<Odysee.Clip> clips = Odysee.videos(list);
        assertEquals(1, clips.size());
        assertEquals("https://player.odycdn.com/v6/streams/abcdef/012345.mp4", clips.get(0).media);
        assertEquals("https://odysee.com/@Chan:99/clip:abcdef", clips.get(0).page);
        assertEquals("Chan", clips.get(0).channel);

        String hidden = "{\"result\":{\"lbry://x\":{"
                + "\"name\":\"secret\",\"claim_id\":\"ffffff\","
                + "\"value\":{\"title\":\"No\",\"tags\":[\"c:unlisted\"],"
                + "\"source\":{\"sd_hash\":\"aabbccdd\"}}}}}";
        assertTrue(Odysee.resolved(hidden).media.isEmpty());
    }
}
