package app.cuepipe;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Bilibili, learned from the Grayjay plugin's public calls.
 * Play URLs are WBI-signed. The picture and the audio are separate files.
 * shortcut: subtitles need a login, so this slice does not request them.
 */
public final class Bili {
    public static final String NAV = "https://api.bilibili.com/x/web-interface/nav";
    public static final String HEADER = "https://s1.hdslb.com/bfs/seed/laputa-header/bili-header.umd.js";
    public static final String SPI = "https://api.bilibili.com/x/frontend/finger/spi";
    public static final String HOME =
            "https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd"
                    + "?fresh_idx=0&ps=20&fresh_type=4&feed_version=V_WATCHLATER_PIP_WINDOW3"
                    + "&fresh_idx_1h=0&brush=0";
    private static final String SEARCH = "https://api.bilibili.com/x/web-interface/wbi/search/type";
    private static final String VIEW = "https://api.bilibili.com/x/web-interface/view";
    private static final String PLAY = "https://api.bilibili.com/x/player/wbi/playurl";
    private static final String SPACE = "https://api.bilibili.com/x/space/wbi/arc/search";
    private static final String PAGE = "https://www.bilibili.com/video/";
    private static final String CHANNEL = "https://space.bilibili.com/";

    public static final class Clip {
        public final String id;
        public final String title;
        public final String page;
        public final String thumb;
        public final String channel;
        public final String channelId;
        public final String channelPage;
        public final String description;
        public final String cid;

        Clip(String id, String title, String page, String thumb, String channel, String channelId,
                String channelPage, String description, String cid) {
            this.id = id;
            this.title = title;
            this.page = page;
            this.thumb = thumb;
            this.channel = channel;
            this.channelId = channelId;
            this.channelPage = channelPage;
            this.description = description;
            this.cid = cid;
        }
    }

    /** One picture URL and one audio URL. Either may be empty. */
    public static final class Pair {
        public final String video;
        public final String audio;

        Pair(String video, String audio) {
            this.video = video == null ? "" : video;
            this.audio = audio == null ? "" : audio;
        }
    }

    private Bili() {
    }

    public static String bvid(String url) {
        if (url == null) {
            return null;
        }
        int at = url.toUpperCase(Locale.ROOT).indexOf("/VIDEO/BV");
        int raw = url.toUpperCase(Locale.ROOT).indexOf("BV");
        int start = at >= 0 ? at + "/VIDEO/".length() : raw;
        if (start < 0 || start + 12 > url.length()) {
            return null;
        }
        String id = url.substring(start, start + 12);
        if (!id.toUpperCase(Locale.ROOT).startsWith("BV")) {
            return null;
        }
        for (int i = 2; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                return null;
            }
        }
        return id;
    }

    public static String mid(String url) {
        if (url == null) {
            return null;
        }
        String marker = "space.bilibili.com/";
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

    public static String viewUrl(String bvid) {
        return VIEW + "?bvid=" + bvid;
    }

    public static String playUrl(String bvid, String cid, String mixin, long seconds) {
        Map<String, String> params = new TreeMap<>();
        params.put("bvid", bvid);
        params.put("cid", cid);
        params.put("fnval", "16");
        params.put("qn", "80");
        return sign(PLAY, params, mixin, seconds);
    }

    public static String searchUrl(String query, String mixin, long seconds) {
        Map<String, String> params = new TreeMap<>();
        params.put("search_type", "video");
        params.put("page", "1");
        params.put("page_size", "20");
        params.put("keyword", query == null ? "" : query);
        params.put("dm_img_list", "[]");
        params.put("dm_img_str", chop64("WebGL 1.0"));
        params.put("dm_cover_img_str", chop64("Intel(R) HD Graphics 400, or similarIntelg"));
        params.put("dm_img_inter", "{\"ds\":[],\"wh\":[100,200,5],\"of\":[10,20,10]}");
        return sign(SEARCH, params, mixin, seconds);
    }

    public static String spaceUrl(String mid, String mixin, long seconds) {
        Map<String, String> params = new TreeMap<>();
        params.put("mid", mid);
        params.put("pn", "1");
        params.put("ps", "20");
        return sign(SPACE, params, mixin, seconds);
    }

    public static String mixinKey(String navJson, String headerJs) {
        Object img = field(field(field(parse(navJson), "data"), "wbi_img"), "img_url");
        Object sub = field(field(field(parse(navJson), "data"), "wbi_img"), "sub_url");
        String raw = slice(text(img)) + slice(text(sub));
        if (raw.length() < 32) {
            return "";
        }
        int[] table = mixinTable(headerJs);
        StringBuilder out = new StringBuilder();
        for (int index : table) {
            if (index >= 0 && index < raw.length()) {
                out.append(raw.charAt(index));
            }
        }
        return out.length() >= 32 ? out.substring(0, 32) : "";
    }

    public static String buvid(String spiJson) {
        return text(field(field(parse(spiJson), "data"), "b_3"));
    }

    public static List<Clip> home(String json) {
        List<Clip> out = new ArrayList<>();
        Object items = field(field(parse(json), "data"), "item");
        if (!(items instanceof List)) {
            return out;
        }
        for (Object item : (List<?>) items) {
            if (!"av".equals(text(field(item, "goto")))) {
                continue;
            }
            Clip clip = fromItem(item, text(field(item, "bvid")), text(field(field(item, "owner"), "name")),
                    text(field(field(item, "owner"), "mid")), text(field(item, "pic")), "");
            if (!clip.id.isEmpty()) {
                out.add(clip);
            }
        }
        return out;
    }

    public static List<Clip> search(String json) {
        List<Clip> out = new ArrayList<>();
        Object items = field(field(parse(json), "data"), "result");
        if (!(items instanceof List)) {
            return out;
        }
        for (Object item : (List<?>) items) {
            if (!"video".equals(text(field(item, "type")))) {
                continue;
            }
            Clip clip = fromItem(item, text(field(item, "bvid")), text(field(item, "author")),
                    text(field(item, "mid")), text(field(item, "pic")), "");
            if (!clip.id.isEmpty()) {
                out.add(clip);
            }
        }
        return out;
    }

    public static List<Clip> space(String json) {
        List<Clip> out = new ArrayList<>();
        Object items = field(field(field(parse(json), "data"), "list"), "vlist");
        if (!(items instanceof List)) {
            return out;
        }
        for (Object item : (List<?>) items) {
            Clip clip = fromItem(item, text(field(item, "bvid")), text(field(item, "author")),
                    text(field(item, "mid")), text(field(item, "pic")), "");
            if (!clip.id.isEmpty()) {
                out.add(clip);
            }
        }
        return out;
    }

    public static Clip video(String json) {
        Object data = field(parse(json), "data");
        Object owner = field(data, "owner");
        String plain = text(field(data, "desc")).replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        if (plain.length() > 320) {
            plain = plain.substring(0, 320) + "…";
        }
        return fromItem(data, text(field(data, "bvid")), text(field(owner, "name")),
                text(field(owner, "mid")), text(field(data, "pic")), plain);
    }

    /**
     * Picks a picture near {@code targetHeight} and an audio file.
     * A negative target means 720. Zero means audio only.
     * shortcut: bilivideo.com returned 403 from this network; akamaized returned the file.
     */
    public static Pair choose(String playJson, int targetHeight) {
        Object dash = field(field(parse(playJson), "data"), "dash");
        String audio = prefer(urlsOf(field(dash, "audio")));
        if (targetHeight == 0) {
            return new Pair("", audio);
        }
        int target = targetHeight < 0 ? 720 : targetHeight;
        String video = "";
        int bestGap = Integer.MAX_VALUE;
        boolean bestAvc = false;
        Object videos = field(dash, "video");
        if (videos instanceof List) {
            for (Object item : (List<?>) videos) {
                int height = number(text(field(item, "height")));
                if (height <= 0) {
                    continue;
                }
                String url = prefer(urlsOf(item));
                if (url.isEmpty()) {
                    continue;
                }
                boolean avc = text(field(item, "codecs")).contains("avc");
                int gap = Math.abs(height - target);
                if (video.isEmpty() || gap < bestGap || (gap == bestGap && avc && !bestAvc)) {
                    video = url;
                    bestGap = gap;
                    bestAvc = avc;
                }
            }
        }
        return new Pair(video, audio);
    }

    private static Clip fromItem(Object item, String id, String channel, String channelId,
            String thumb, String description) {
        String cid = text(field(item, "cid"));
        return new Clip(
                id,
                plain(text(field(item, "title"))),
                id.isEmpty() ? "" : PAGE + id,
                https(thumb),
                channel,
                channelId,
                channelId.isEmpty() ? "" : CHANNEL + channelId,
                description,
                cid);
    }

    private static String plain(String value) {
        return value.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }

    private static String https(String url) {
        if (url.startsWith("//")) {
            return "https:" + url;
        }
        if (url.startsWith("http://")) {
            return "https://" + url.substring("http://".length());
        }
        return url;
    }

    private static String slice(String url) {
        if (url.length() < 61) {
            return "";
        }
        return url.substring(29, 61);
    }

    private static String sign(String base, Map<String, String> params, String mixin, long seconds) {
        Map<String, String> sorted = new TreeMap<>(params);
        sorted.put("wts", Long.toString(seconds));
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(entry.getKey()).append('=').append(encode(entry.getValue()));
        }
        return base + "?" + query + "&w_rid=" + md5(query + mixin);
    }

    private static String encode(String value) {
        String encoded = URLEncoder.encode(value, StandardCharsets.UTF_8);
        return encoded.replace("+", "%20")
                .replace("%21", "!")
                .replace("%27", "'")
                .replace("%28", "(")
                .replace("%29", ")")
                .replace("%7E", "~");
    }

    private static String md5(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                out.append(String.format(Locale.ROOT, "%02x", b));
            }
            return out.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String chop64(String value) {
        String encoded = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        int pad = (4 - encoded.length() % 4) % 4;
        encoded = encoded + "=".repeat(pad);
        return encoded.substring(0, encoded.length() - 2);
    }

    private static int[] mixinTable(String js) {
        if (js == null) {
            return new int[0];
        }
        int at = 0;
        while (at < js.length()) {
            int hit = js.indexOf(".charAt(", at);
            if (hit < 0) {
                break;
            }
            int search = hit;
            while (search > 0) {
                int bracket = js.lastIndexOf('[', search - 1);
                int end = bracket < 0 ? -1 : js.indexOf(']', bracket);
                if (bracket < 0 || hit - bracket > 900 || end < 0 || end > hit) {
                    break;
                }
                String body = js.substring(bracket + 1, end);
                int commas = 0;
                for (int i = 0; i < body.length(); i++) {
                    if (body.charAt(i) == ',') {
                        commas++;
                    }
                }
                if (commas >= 60) {
                    String[] parts = body.split(",");
                    int[] table = new int[parts.length];
                    for (int i = 0; i < parts.length; i++) {
                        table[i] = number(parts[i].trim());
                    }
                    return table;
                }
                search = bracket;
            }
            at = hit + 8;
        }
        return new int[0];
    }

    private static List<String> urlsOf(Object node) {
        List<String> urls = new ArrayList<>();
        if (node instanceof List) {
            for (Object item : (List<?>) node) {
                urls.addAll(urlsOf(item));
            }
            return urls;
        }
        if (node instanceof Map || node instanceof String) {
            add(urls, text(node instanceof String ? node : field(node, "base_url")));
            Object backup = field(node, "backup_url");
            if (backup instanceof List) {
                for (Object item : (List<?>) backup) {
                    add(urls, text(item));
                }
            }
        }
        return urls;
    }

    private static void add(List<String> urls, String url) {
        if (url != null && !url.isEmpty()) {
            urls.add(url);
        }
    }

    private static String prefer(List<String> urls) {
        String any = "";
        for (String url : urls) {
            if (any.isEmpty()) {
                any = url;
            }
            if (url.contains("akamaized")) {
                return url;
            }
        }
        return any;
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
