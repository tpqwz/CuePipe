package app.cuepipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * BitChute, learned from the Grayjay plugin's public JSON calls.
 * One media URL per video. No caption file in that response.
 */
public final class Chute {
    public static final String POPULAR = "https://api.bitchute.com/api/beta/videos";
    public static final String SEARCH = "https://api.bitchute.com/api/beta/search/videos";
    public static final String CHANNEL = "https://api.bitchute.com/api/beta/channel/videos";
    public static final String MEDIA = "https://api.bitchute.com/api/beta/video/media";
    public static final String VIDEO = "https://api.bitchute.com/api/beta9/video";
    private static final String WEB = "https://www.bitchute.com";

    public static final class Clip {
        public final String id;
        public final String title;
        public final String page;
        public final String thumb;
        public final String channel;
        public final String channelId;
        public final String channelPage;
        public final String description;

        Clip(String id, String title, String page, String thumb, String channel,
                String channelId, String channelPage, String description) {
            this.id = id;
            this.title = title;
            this.page = page;
            this.thumb = thumb;
            this.channel = channel;
            this.channelId = channelId;
            this.channelPage = channelPage;
            this.description = description;
        }
    }

    private Chute() {
    }

    public static String videoId(String url) {
        return pathId(url, "bitchute.com/video/", "playlist");
    }

    public static String channelId(String url) {
        return pathId(url, "bitchute.com/channel/", "");
    }

    public static String popularBody() {
        return "{\"selection\":\"popular\",\"offset\":0,\"limit\":20,"
                + "\"advertisable\":true,\"sensitivity_id\":\"normal\"}";
    }

    public static String searchBody(String query) {
        return "{\"offset\":0,\"limit\":20,\"query\":" + quote(query)
                + ",\"sensitivity_id\":\"normal\",\"sort\":\"new\"}";
    }

    public static String channelBody(String id) {
        return "{\"offset\":0,\"limit\":20,\"channel_id\":" + quote(id) + "}";
    }

    public static String idBody(String id) {
        return "{\"video_id\":" + quote(id) + "}";
    }

    /** Rows from a popular, search, or channel response. */
    public static List<Clip> videos(String json) {
        List<Clip> out = new ArrayList<>();
        Object root = parse(json);
        Object videos = field(root, "videos");
        if (!(videos instanceof List)) {
            return out;
        }
        for (Object item : (List<?>) videos) {
            Clip clip = clip(item, "");
            if (!clip.id.isEmpty() && !clip.page.isEmpty()) {
                out.add(clip);
            }
        }
        return out;
    }

    /** One video from the beta9 detail response. */
    public static Clip video(String json) {
        return clip(parse(json), text(field(parse(json), "description")));
    }

    public static String media(String json) {
        return text(field(parse(json), "media_url"));
    }

    private static Clip clip(Object item, String description) {
        String id = text(field(item, "video_id"));
        String path = text(field(item, "video_url"));
        if (id.isEmpty()) {
            id = videoId(absolute(path));
        }
        Object channel = field(item, "channel");
        String channelId = text(field(channel, "channel_id"));
        String channelPath = text(field(channel, "channel_url"));
        String plain = description.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        if (plain.length() > 320) {
            plain = plain.substring(0, 320) + "…";
        }
        return new Clip(
                id == null ? "" : id,
                text(field(item, "video_name")),
                absolute(path.isEmpty() && !id.isEmpty() ? "/video/" + id + "/" : path),
                text(field(item, "thumbnail_url")),
                text(field(channel, "channel_name")),
                channelId,
                absolute(channelPath.isEmpty() && !channelId.isEmpty()
                        ? "/channel/" + channelId + "/" : channelPath),
                plain);
    }

    private static String pathId(String url, String marker, String reject) {
        if (url == null) {
            return null;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        int at = lower.indexOf(marker);
        if (at < 0) {
            return null;
        }
        String rest = url.substring(at + marker.length());
        int end = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') {
                end = i;
                break;
            }
        }
        String id = rest.substring(0, end);
        if (id.isEmpty() || id.equalsIgnoreCase(reject)) {
            return null;
        }
        return id;
    }

    private static String absolute(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        if (path.startsWith("/")) {
            return WEB + path;
        }
        return WEB + "/" + path;
    }

    private static Object field(Object node, String key) {
        if (node instanceof Map) {
            return ((Map<?, ?>) node).get(key);
        }
        return null;
    }

    private static String text(Object value) {
        return value instanceof String ? (String) value : "";
    }

    static String quote(String raw) {
        String value = raw == null ? "" : raw;
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c >= 0x20) {
                out.append(c);
            }
        }
        return out.append('"').toString();
    }

    private static Object parse(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return new Parser(json).value();
    }

    private static final class Parser {
        private final String text;
        private int at;

        Parser(String text) {
            this.text = text;
        }

        Object value() {
            skip();
            if (at >= text.length()) {
                return null;
            }
            char c = text.charAt(at);
            if (c == '{') {
                return object();
            }
            if (c == '[') {
                return array();
            }
            if (c == '"') {
                return string();
            }
            if (c == 't' || c == 'f') {
                at += c == 't' ? 4 : 5;
                return c == 't';
            }
            if (c == 'n') {
                at += 4;
                return null;
            }
            while (at < text.length() && "0123456789+-.eE".indexOf(text.charAt(at)) >= 0) {
                at++;
            }
            return null;
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            at++;
            skip();
            if (at < text.length() && text.charAt(at) == '}') {
                at++;
                return map;
            }
            while (at < text.length()) {
                skip();
                String key = string();
                skip();
                if (at < text.length() && text.charAt(at) == ':') {
                    at++;
                }
                map.put(key, value());
                skip();
                if (at >= text.length() || text.charAt(at) != ',') {
                    break;
                }
                at++;
            }
            if (at < text.length() && text.charAt(at) == '}') {
                at++;
            }
            return map;
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            at++;
            skip();
            if (at < text.length() && text.charAt(at) == ']') {
                at++;
                return list;
            }
            while (at < text.length()) {
                list.add(value());
                skip();
                if (at >= text.length() || text.charAt(at) != ',') {
                    break;
                }
                at++;
            }
            if (at < text.length() && text.charAt(at) == ']') {
                at++;
            }
            return list;
        }

        private String string() {
            if (at >= text.length() || text.charAt(at) != '"') {
                return "";
            }
            at++;
            StringBuilder out = new StringBuilder();
            while (at < text.length()) {
                char c = text.charAt(at++);
                if (c == '"') {
                    break;
                }
                if (c != '\\' || at >= text.length()) {
                    out.append(c);
                    continue;
                }
                char esc = text.charAt(at++);
                if (esc == 'n') {
                    out.append('\n');
                } else if (esc == 'u' && at + 4 <= text.length()) {
                    out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    at += 4;
                } else {
                    out.append(esc);
                }
            }
            return out.toString();
        }

        private void skip() {
            while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
                at++;
            }
        }
    }
}
