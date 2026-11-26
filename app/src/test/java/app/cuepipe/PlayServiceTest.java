package app.cuepipe;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class PlayServiceTest {
    @Test
    public void aGzipPlaylistBecomesExtm3u() throws Exception {
        byte[] gz = gzip("#EXTM3U\n#EXT-X-ENDLIST\n");
        okhttp3.Response response = response("https://nasaplus.akamaized.net/output/17935-4.m3u8", 206, gz)
                .newBuilder()
                .header("Content-Encoding", "gzip")
                .header("Content-Range", "bytes 0-" + (gz.length - 1) + "/" + gz.length)
                .build();
        okhttp3.Response plain = PlayService.playlist(response);
        assertEquals(200, plain.code());
        assertNull(plain.header("Content-Encoding"));
        assertNull(plain.header("Content-Range"));
        assertTrue(plain.body().string().startsWith("#EXTM3U"));
    }

    @Test
    public void aPlainPlaylistStaysPlain() throws Exception {
        byte[] text = "#EXTM3U\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        okhttp3.Response plain = PlayService.playlist(
                response("https://nasaplus.akamaized.net/output/17935.m3u8", 200, text));
        assertEquals(200, plain.code());
        assertEquals("#EXTM3U\n", plain.body().string());
    }

    private static okhttp3.Response response(String url, int code, byte[] body) {
        return new okhttp3.Response.Builder()
                .request(new okhttp3.Request.Builder().url(url).build())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(code)
                .message(code == 200 ? "OK" : "Partial Content")
                .body(okhttp3.ResponseBody.create(body, okhttp3.MediaType.get("application/x-mpegURL")))
                .build();
    }

    private static byte[] gzip(String text) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream zip = new GZIPOutputStream(out)) {
            zip.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }
}
