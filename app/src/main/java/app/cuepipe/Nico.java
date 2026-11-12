package app.cuepipe;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Niconico, learned from the Grayjay plugin.
 * The watch page embeds the media list. A POST then returns one HLS playlist.
 * shortcut: the plugin's v1 search now 404s, so search uses v2. Live streams stay out.
 */
public final class Nico {
    public static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0";
    public static final String HOME =
            "https://nvapi.nicovideo.jp/v1/recommend?recipeId=video_recommendation_recommend"
                    + "&sensitiveContents=mask&site=nicovideo&_frontendId=6&_frontendVersion=0";
    private static final String WATCH = "https://www.nicovideo.jp/watch/";
    private static final String USER = "https://www.nicovideo.jp/user/";

    public static final class Clip {
        public final String id;
        public final String title;
        public final String page;
        public final String thumb;
        public final String channel;
        public final String channelId;
        public final String channelPage;
        public final String description;

        Clip(String id, String title, String page, String thumb, String channel, String channelId,
                String channelPage, String description) {
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

    public static final class Watch {
        public final Clip clip;
        public final String accessKey;
        public final String trackId;
        public final List<Rendition> videos;
        public final List<Rendition> audios;

        Watch(Clip clip, String accessKey, String trackId, List<Rendition> videos, List<Rendition> audios) {
            this.clip = clip;
            this.accessKey = accessKey;
            this.trackId = trackId;
            this.videos = videos;
            this.audios = audios;
        }
    }

    public static final class Rendition {
        public final String id;
        public final int height;
        public final boolean available;

        Rendition(String id, int height, boolean available) {
            this.id = id;
            this.height = height;
            this.available = available;
        }
    }

    private Nico() {
    }

    public static String videoId(String url) {
        if (url == null) {
            return null;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        int at = lower.indexOf("/watch/");
        if (at < 0) {
            return null;
        }
        String rest = url.substring(at + "/watch/".length());
        int end = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                end = i;
                break;
            }
        }
        String id = rest.substring(0, end);
        String kind = id.length() >= 2 ? id.substring(0, 2).toLowerCase(Locale.ROOT) : "";
        if (!kind.equals("sm") && !kind.equals("so") && !kind.equals("nm")) {
            return null;
        }
        return id;
    }

    public static String userId(String url) {
        if (url == null) {
            return null;
        }
        String marker = "nicovideo.jp/user/";
        int at = url.toLowerCase(Locale.ROOT).indexOf(marker);
        if (at < 0) {
            return null;
        }
        String rest = url.substring(at + marker.length());
        int end = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            if (!Character.isDigit(rest.charAt(i))) {
                end = i;
                break;
            }
        }
        String id = rest.substring(0, end);
        return id.isEmpty() ? null : id;
    }

    public static String searchUrl(String query) {
        return "https://nvapi.nicovideo.jp/v2/search/video?keyword=" + encode(query)
                + "&sortKey=registeredAt&sortOrder=desc&page=1&pageSize=20";
    }

    public static String userUrl(String id) {
        return "https://nvapi.nicovideo.jp/v3/users/" + id
                + "/videos?sensitiveContents=mask&sortOrder=desc&pageSize=20&sortKey=registeredAt&page=1";
    }

    public static String hlsUrl(String videoId, String trackId) {
        return "https://nvapi.nicovideo.jp/v1/watch/" + videoId
                + "/access-rights/hls?actionTrackId=" + encode(trackId);
    }

    public static String hlsBody(String videoId, String audioId) {
        return "{\"outputs\":[[" + quote(videoId) + "," + quote(audioId) + "]]}";
    }

    public static List<Clip> videos(String json) {
        List<Clip> out = new ArrayList<>();
        Object items = field(field(parse(json), "data"), "items");
        if (!(items instanceof List)) {
            return out;
        }
        for (Object item : (List<?>) items) {
            Clip clip = clip(videoNode(item), "");
            if (!clip.id.isEmpty()) {
                out.add(clip);
            }
        }
        return out;
    }

    /** The media list embedded in a watch page. */
    public static Watch watch(String html) {
        Object root = parse(embedded(html));
        Object data = field(field(field(field(root, "data"), "response"), "$watchV4"), "data");
        Object media = field(data, "media");
        Object contents = field(media, "contents");
        Object video = field(data, "video");
        Object owner = field(field(field(data, "metadata"), "jsonLd"), "owner");
        String plain = text(field(video, "description")).replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ").trim();
        if (plain.length() > 320) {
            plain = plain.substring(0, 320) + "…";
        }
        Clip clip = new Clip(
                text(field(video, "id")),
                text(field(video, "title")),
                text(field(video, "id")).isEmpty() ? "" : WATCH + text(field(video, "id")),
                thumb(field(video, "thumbnail")),
                text(field(owner, "name")),
                text(field(owner, "id")),
                text(field(owner, "id")).isEmpty() ? "" : USER + text(field(owner, "id")),
                plain);
        return new Watch(
                clip,
                text(field(media, "accessRightKey")),
                text(field(field(data, "client"), "watchTrackId")),
                renditions(field(contents, "videos")),
                renditions(field(contents, "audios")));
    }

    /** Video id and audio id for the requested height. A negative height means 720. Zero means the lowest. */
    public static String[] pair(Watch watch, int targetHeight) {
        Rendition audio = null;
        for (Rendition rendition : watch.audios) {
            if (rendition.available) {
                audio = rendition;
                break;
            }
        }
        Rendition video = null;
        int target = targetHeight < 0 ? 720 : targetHeight;
        int best = Integer.MAX_VALUE;
        for (Rendition rendition : watch.videos) {
            if (!rendition.available || rendition.height <= 0) {
                continue;
            }
            int gap = targetHeight == 0 ? rendition.height : Math.abs(rendition.height - target);
            if (video == null || gap < best) {
                video = rendition;
                best = gap;
            }
        }
        if (video == null || audio == null) {
            return new String[] {"", ""};
        }
        return new String[] {video.id, audio.id};
    }

    public static String contentUrl(String json) {
        return text(field(field(parse(json), "data"), "contentUrl"));
    }

    public static String cookie(String setCookie) {
        if (setCookie == null) {
            return "";
        }
        int at = setCookie.indexOf("nicosid=");
        if (at < 0) {
            return "";
        }
        int end = setCookie.indexOf(';', at);
        String pair = end < 0 ? setCookie.substring(at) : setCookie.substring(at, end);
        return pair.startsWith("nicosid=") && pair.length() > "nicosid=".length() ? pair.trim() : "";
    }

    static String embedded(String html) {
        if (html == null) {
            return "";
        }
        int at = html.indexOf("name=\"server-response\"");
        if (at < 0) {
            return html.trim().startsWith("{") ? html : "";
        }
        int content = html.indexOf("content=\"", at);
        if (content < 0) {
            return "";
        }
        int start = content + "content=\"".length();
        int end = html.indexOf('"', start);
        if (end < 0) {
            return "";
        }
        return unescape(html.substring(start, end));
    }

    private static Object videoNode(Object item) {
        Object essential = field(item, "essential");
        if (essential instanceof Map) {
            return essential;
        }
        Object content = field(item, "content");
        if (content instanceof Map && !text(field(content, "id")).isEmpty()) {
            return content;
        }
        return item;
    }

    private static Clip clip(Object node, String description) {
        String id = text(field(node, "id"));
        if (videoId(WATCH + id) == null) {
            id = "";
        }
        Object owner = field(node, "owner");
        String channelId = text(field(owner, "id"));
        return new Clip(
                id,
                text(field(node, "title")),
                id.isEmpty() ? "" : WATCH + id,
                thumb(field(node, "thumbnail")),
                text(field(owner, "name")),
                channelId,
                channelId.isEmpty() ? "" : USER + channelId,
                description);
    }

    private static String thumb(Object thumbnail) {
        String listing = text(field(thumbnail, "listingUrl"));
        if (!listing.isEmpty()) {
            return listing;
        }
        String url = text(field(thumbnail, "url"));
        if (!url.isEmpty()) {
            return url;
        }
        return text(field(thumbnail, "normal"));
    }

    private static List<Rendition> renditions(Object node) {
        List<Rendition> out = new ArrayList<>();
        if (!(node instanceof List)) {
            return out;
        }
        for (Object item : (List<?>) node) {
            out.add(new Rendition(
                    text(field(item, "id")),
                    number(text(field(item, "height"))),
                    "true".equals(text(field(item, "isAvailable")))));
        }
        return out;
    }

    private static String unescape(String value) {
        return value.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#34;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private static String encode(String value) {
        String raw = value == null ? "" : value;
        return URLEncoder.encode(raw, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String quote(String value) {
        return "\"" + (value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")) + "\"";
    }

    private static int number(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
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
                return c == 't' ? "true" : "false";
            }
            if (c == 'n') {
                at += 4;
                return null;
            }
            int start = at;
            while (at < text.length() && "0123456789+-.eE".indexOf(text.charAt(at)) >= 0) {
                at++;
            }
            return text.substring(start, at);
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
