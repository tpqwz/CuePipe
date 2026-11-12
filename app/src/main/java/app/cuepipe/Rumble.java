package app.cuepipe;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rumble, learned from the Grayjay plugin's embed stream map.
 * embedJS is behind Cloudflare here. The embed page carries the same
 * {@code ua.mp4} list, and oEmbed points a watch link at that page.
 */
public final class Rumble {
    private static final Pattern STREAM = Pattern.compile(
            "\"(\\d+)\":\\{\"url\":\"(https:[^\"]+\\.mp4)\"");
    private static final Pattern EMBED = Pattern.compile("/embed/(v[0-9a-z]+)", Pattern.CASE_INSENSITIVE);

    public static final class Clip {
        public final String title;
        public final String author;
        public final String authorUrl;
        public final String thumb;
        public final String embed;

        Clip(String title, String author, String authorUrl, String thumb, String embed) {
            this.title = title;
            this.author = author;
            this.authorUrl = authorUrl;
            this.thumb = thumb;
            this.embed = embed;
        }
    }

    public static final class Stream {
        public final int height;
        public final String url;

        Stream(int height, String url) {
            this.height = height;
            this.url = url;
        }
    }

    private Rumble() {
    }

    public static boolean isWatch(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("rumble.com/v") && !lower.contains("/embed/");
    }

    public static String embedId(String url) {
        if (url == null) {
            return null;
        }
        Matcher matcher = EMBED.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static String oembedUrl(String page) {
        return "https://rumble.com/api/Media/oembed.json?url=" + encode(page);
    }

    public static String embedPage(String id) {
        return "https://rumble.com/embed/" + id + "/";
    }

    public static Clip oembed(String json) {
        String html = stringField(json, "html");
        String src = "";
        int at = html.indexOf("src=\"");
        if (at >= 0) {
            int end = html.indexOf('"', at + 5);
            if (end > at) {
                src = html.substring(at + 5, end);
            }
        }
        return new Clip(
                stringField(json, "title"),
                stringField(json, "author_name"),
                stringField(json, "author_url"),
                stringField(json, "thumbnail_url"),
                src);
    }

    /** Heights and files from the embed page, the same map embedJS returns. */
    public static List<Stream> streams(String html) {
        List<Stream> out = new ArrayList<>();
        if (html == null) {
            return out;
        }
        Matcher matcher = STREAM.matcher(html);
        while (matcher.find()) {
            String url = matcher.group(2).replace("\\/", "/");
            int height = Integer.parseInt(matcher.group(1));
            boolean seen = false;
            for (Stream stream : out) {
                if (stream.height == height) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                out.add(new Stream(height, url));
            }
        }
        return out;
    }

    /** A negative height means 720. Zero means the lowest file. */
    public static String choose(List<Stream> streams, int targetHeight) {
        if (streams == null || streams.isEmpty()) {
            return "";
        }
        if (targetHeight == 0) {
            Stream low = streams.get(0);
            for (Stream stream : streams) {
                if (stream.height < low.height) {
                    low = stream;
                }
            }
            return low.url;
        }
        int target = targetHeight < 0 ? 720 : targetHeight;
        Stream best = null;
        int gap = Integer.MAX_VALUE;
        for (Stream stream : streams) {
            int next = Math.abs(stream.height - target);
            if (best == null || next < gap) {
                best = stream;
                gap = next;
            }
        }
        return best == null ? "" : best.url;
    }

    public static String pageTitle(String html) {
        if (html == null) {
            return "";
        }
        int start = html.indexOf("<title>");
        int end = html.indexOf("</title>");
        if (start < 0 || end <= start) {
            return "";
        }
        String title = html.substring(start + "<title>".length(), end).trim();
        if (title.endsWith(" - Rumble")) {
            title = title.substring(0, title.length() - " - Rumble".length()).trim();
        }
        return title;
    }

    public static boolean challenged(String body) {
        return body != null && (body.contains("Just a moment") || body.contains("cf-mitigated"));
    }

    private static String stringField(String json, String key) {
        if (json == null) {
            return "";
        }
        String mark = "\"" + key + "\":\"";
        int at = json.indexOf(mark);
        if (at < 0) {
            return "";
        }
        int i = at + mark.length();
        StringBuilder out = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i++);
            if (c == '\\' && i < json.length()) {
                char escaped = json.charAt(i++);
                if (escaped == 'n') {
                    out.append('\n');
                } else if (escaped == '/') {
                    out.append('/');
                } else {
                    out.append(escaped);
                }
            } else if (c == '"') {
                break;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
