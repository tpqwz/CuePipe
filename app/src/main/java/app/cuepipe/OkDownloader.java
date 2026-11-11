package app.cuepipe;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

/** HTTP for NewPipeExtractor. The extractor supplies the headers a site requires. */
public final class OkDownloader extends Downloader {
    static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    @Override
    public Response execute(Request request) throws IOException, ReCaptchaException {
        okhttp3.Request.Builder builder = new okhttp3.Request.Builder().url(request.url());
        boolean hasAgent = false;
        Map<String, List<String>> headers = request.headers();
        if (headers != null) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                if (entry.getKey().equalsIgnoreCase("User-Agent")) {
                    hasAgent = true;
                }
                for (String value : entry.getValue()) {
                    if (value != null) {
                        builder.addHeader(entry.getKey(), value);
                    }
                }
            }
        }
        if (!hasAgent) {
            builder.header("User-Agent", USER_AGENT);
        }

        byte[] data = request.dataToSend();
        String method = request.httpMethod();
        if ("POST".equals(method)) {
            MediaType type = null;
            String contentType = header(headers, "Content-Type");
            if (contentType != null) {
                type = MediaType.parse(contentType);
            }
            builder.post(RequestBody.create(data == null ? new byte[0] : data, type));
        } else if ("HEAD".equals(method)) {
            builder.head();
        } else if ("PUT".equals(method)) {
            builder.put(RequestBody.create(data == null ? new byte[0] : data, null));
        } else {
            builder.get();
        }

        try (okhttp3.Response resp = client.newCall(builder.build()).execute()) {
            if (resp.code() == 429) {
                throw new ReCaptchaException("reCaptcha Challenge requested", request.url());
            }
            String body = resp.body() == null ? "" : resp.body().string();
            String latest = resp.request().url().toString();
            return new Response(
                    resp.code(),
                    resp.message(),
                    resp.headers().toMultimap(),
                    body,
                    latest);
        }
    }

    private static String header(Map<String, List<String>> headers, String name) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null
                    && entry.getKey().equalsIgnoreCase(name)
                    && entry.getValue() != null
                    && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return null;
    }
}
