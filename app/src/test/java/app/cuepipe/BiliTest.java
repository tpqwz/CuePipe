package app.cuepipe;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class BiliTest {
    @Test
    public void anOpenBilibiliRangeClosesOnTheLastByte() {
        assertTrue(Bili.cdnHost("upos-hz-mirrorakam.akamaized.net", "/upgcxcode/1.m4s"));
        assertTrue(Bili.cdnHost("upos-sz-mirrorcosov.bilivideo.com", "/a.m4s"));
        assertFalse(Bili.cdnHost("nasaplus.akamaized.net", "/output/1.m3u8"));
        assertFalse(Bili.cdnHost("player.odycdn.com", "/v6/streams/a/b.mp4"));
        assertEquals(59552563L, Bili.documentSize("bytes 0-1/59552563"));
        assertEquals(-1L, Bili.documentSize("bytes */*"));
        assertEquals("bytes=0-59552562", Bili.closedRange(null, 59552563L));
        assertEquals("bytes=1000-59552562", Bili.closedRange("bytes=1000-", 59552563L));
        assertEquals("bytes=0-32", Bili.closedRange("bytes=0-32", 59552563L));
        assertNull(Bili.closedRange(null, -1));
    }

    @Test
    public void aWatchLinkIsTheBvidAndASpaceLinkIsTheMid() {
        assertEquals("BV1kZWX62ENY",
                Bili.bvid("https://www.bilibili.com/video/BV1kZWX62ENY/"));
        assertEquals("946974", Bili.mid("https://space.bilibili.com/946974/video"));
        assertNull(Bili.bvid("https://www.youtube.com/watch?v=abc"));
        assertNull(Bili.mid("https://www.bilibili.com/video/BV1kZWX62ENY"));
    }

    @Test
    public void theMixinKeyIsTheFirst32ShuffledCharacters() {
        String nav = "{\"data\":{\"wbi_img\":{"
                + "\"img_url\":\"https://i0.hdslb.com/bfs/wbi/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA.png\","
                + "\"sub_url\":\"https://i0.hdslb.com/bfs/wbi/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB.png\"}}}";
        StringBuilder nums = new StringBuilder();
        for (int i = 0; i < 64; i++) {
            if (i > 0) {
                nums.append(',');
            }
            nums.append(i);
        }
        String js = "var a=[" + nums + "],b=[];b.forEach(function(n){e.charAt(n)})";
        String mixin = Bili.mixinKey(nav, js);
        assertEquals("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", mixin);
        String signed = Bili.playUrl("BV1kZWX62ENY", "42", mixin, 1);
        assertTrue(signed.contains("bvid=BV1kZWX62ENY"));
        assertTrue(signed.contains("cid=42"));
        assertTrue(signed.contains("wts=1"));
        assertTrue(signed.contains("w_rid="));
        assertEquals(32, signed.substring(signed.indexOf("w_rid=") + 6).length());
    }

    @Test
    public void listsKeepVideosAndThePlayerPrefersAkamaiAvc() {
        String home = "{\"data\":{\"item\":["
                + "{\"goto\":\"ad\"},"
                + "{\"goto\":\"av\",\"bvid\":\"BV1kZWX62ENY\",\"title\":\"<em>Hello</em>\","
                + "\"pic\":\"//i2.hdslb.com/a.jpg\",\"cid\":7,"
                + "\"owner\":{\"name\":\"飓风\",\"mid\":9}}]}}";
        List<Bili.Clip> clips = Bili.home(home);
        assertEquals(1, clips.size());
        assertEquals("Hello", clips.get(0).title);
        assertEquals("https://www.bilibili.com/video/BV1kZWX62ENY", clips.get(0).page);
        assertEquals("https://i2.hdslb.com/a.jpg", clips.get(0).thumb);
        assertEquals("https://space.bilibili.com/9", clips.get(0).channelPage);
        assertEquals("7", clips.get(0).cid);

        String play = "{\"data\":{\"dash\":{"
                + "\"video\":["
                + "{\"height\":360,\"codecs\":\"hev1\",\"base_url\":\"https://upos.bilivideo.com/a.m4s\","
                + "\"backup_url\":[\"https://upos.akamaized.net/upgcxcode/low.m4s\"]},"
                + "{\"height\":720,\"codecs\":\"avc1.640028\",\"base_url\":\"https://upos.bilivideo.com/b.m4s\","
                + "\"backup_url\":[\"https://upos.akamaized.net/upgcxcode/hi.m4s\"]}"
                + "],"
                + "\"audio\":[{\"base_url\":\"https://upos.bilivideo.com/s.m4s\","
                + "\"backup_url\":[\"https://upos.akamaized.net/upgcxcode/a.m4s\"]}]}}}";
        Bili.Pair pair = Bili.choose(play, -1);
        assertEquals("https://upos.akamaized.net/upgcxcode/hi.m4s", pair.video);
        assertEquals("https://upos.akamaized.net/upgcxcode/a.m4s", pair.audio);
        assertEquals("", Bili.choose(play, 0).video);
    }
}
