package app.cuepipe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class ArchiveTest {
    @Test
    public void aDetailsLinkIsTheIdentifier() {
        assertEquals("AboutBan1935",
                Archive.identifier("https://archive.org/details/AboutBan1935?x=1"));
        assertEquals("AboutBan1935",
                Archive.identifier("https://archive.org/download/AboutBan1935/AboutBan1935.mp4"));
        assertNull(Archive.identifier("https://www.youtube.com/watch?v=abc"));
    }

    @Test
    public void searchRowsAndAMetadataFileKeepTheMp4() {
        String docs = "{\"response\":{\"docs\":[{\"identifier\":\"AboutBan1935\",\"title\":\"About Bananas\","
                + "\"creator\":\"Prelinger\"}]}}";
        Archive.Item row = Archive.docs(docs).get(0);
        assertEquals("https://archive.org/details/AboutBan1935", row.page);
        assertEquals("Prelinger", row.creator);

        String meta = "{\"metadata\":{\"identifier\":\"AboutBan1935\",\"title\":\"About Bananas\","
                + "\"creator\":[\"A\",\"B\"]},\"files\":["
                + "{\"name\":\"AboutBan1935.mp4\",\"format\":\"h.264\",\"height\":480,\"source\":\"original\"},"
                + "{\"name\":\"AboutBan1935_512kb.mp4\",\"format\":\"512Kb MPEG4\",\"height\":240},"
                + "{\"name\":\"AboutBan1935.asr.srt\",\"format\":\"SubRip\"},"
                + "{\"name\":\"meta.xml\",\"source\":\"metadata\",\"format\":\"Metadata\"}]}";
        Archive.Item item = Archive.item(meta, -1);
        assertEquals("https://archive.org/download/AboutBan1935/AboutBan1935.mp4", item.media);
        assertEquals("A, B", item.creator);
        assertEquals("en", item.captions.get(0).lang);
        assertEquals("https://archive.org/download/AboutBan1935/AboutBan1935_512kb.mp4",
                Archive.item(meta, 0).media);
    }
}
