package app.cuepipe;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Red Bull TV, learned from the Grayjay plugin.
 * The feed lists videos. Playback is
 * {@code api-player.redbull.com/rbcom/videoresource?videoId=}.
 * English captions are a VTT built from the asset id.
 */
public final class RedBull {
    public static final String HOME =
            "https://www.redbull.com/v3/api/graphql/v1/v3/feed/en-INT"
                    + "?filter[type]=videos,films,episode-videos&page[limit]=20&page[offset]=0"
                    + "&scoring=freshness&spaces=rbtv&rb3Schema=v1:cardList";

    public static final class Caption {
        public final String lang;
        public final String url;

        Caption(String lang, String url) {
            this.lang = lang;
            this.url = url;
        }
    }

    public static final class Clip {
        public final String id;
        public final String title;
        public final String page;
        public final String thumb;
        public final String media;
        public final List<Caption> captions;

        Clip(String id, String title, String page, String thumb, String media, List<Caption> captions) {
            this.id = id;
            this.title = title;
            this.page = page;
            this.thumb = thumb;
            this.media = media;
            this.captions = captions;
        }
    }

    private RedBull() {
    }

    public static String searchUrl(String query) {
        return "https://www.redbull.com/v3/api/graphql/v1/v3/feed/en-INT?rb3SearchString="
                + encode(query) + "&rb3SearchTab=all&rb3Schema=v1:searchResults"
                + "&page[limit]=20&page[offset]=0";
    }

    public static String playerUrl(String id) {
        return "https://api-player.redbull.com/rbcom/videoresource?videoId=" + encode(id);
    }

    public static String page(String id) {
        return "https://www.redbull.com/int-en/?video=" + encode(id);
    }

    /** The rrn content id from a row link or a raw id. */
    public static String id(String url) {
        if (url == null) {
            return null;
        }
        int at = url.indexOf("rrn:content:");
        if (at >= 0) {
            String rest = url.substring(at);
            int end = rest.length();
            for (int i = 0; i < rest.length(); i++) {
                char c = rest.charAt(i);
                if (c == '&' || c == '#' || c == ' ' || c == '"') {
                    end = i;
                    break;
                }
            }
            return rest.substring(0, end);
        }
        int video = url.indexOf("video=");
        if (video >= 0) {
            String rest = url.substring(video + "video=".length());
            int end = rest.length();
            for (int i = 0; i < rest.length(); i++) {
                if (rest.charAt(i) == '&') {
                    end = i;
                    break;
                }
            }
            String decoded = decode(rest.substring(0, end));
            return decoded.startsWith("rrn:content:") ? decoded : null;
        }
        return null;
    }

    public static List<Clip> feed(String json) {
        Object root = parse(json);
        Object items = field(root, "data");
        if (items instanceof Map) {
            items = field(items, "results");
            if (!(items instanceof List)) {
                items = field(field(root, "data"), "data");
            }
        }
        List<Clip> out = new ArrayList<>();
        if (!(items instanceof List)) {
            return out;
        }
        for (Object item : (List<?>) items) {
            String id = text(field(item, "id"));
            if (!playable(id)) {
                continue;
            }
            String title = text(field(field(item, "content"), "title"));
            if (title.isEmpty()) {
                title = text(field(item, "title"));
            }
            if (title.isEmpty()) {
                continue;
            }
            out.add(new Clip(id, title, page(id), thumb(item), "", List.of()));
        }
        return out;
    }

    public static Clip player(String json) {
        Object root = parse(json);
        String id = text(field(root, "videoId"));
        String media = text(field(root, "videoUrl"));
        String asset = text(field(root, "assetId"));
        List<Caption> captions = new ArrayList<>();
        Object langs = field(root, "availableSubtitleLanguages");
        if (langs instanceof Map && !asset.isEmpty()) {
            for (Object key : ((Map<?, ?>) langs).keySet()) {
                String locale = text(key);
                if (locale.isEmpty() || captions.size() >= 4) {
                    continue;
                }
                if (!locale.toLowerCase(Locale.ROOT).startsWith("en") && captions.size() > 0) {
                    continue;
                }
                captions.add(new Caption(
                        locale.length() >= 2 ? locale.substring(0, 2) : "en",
                        "https://play.redbull.com/vtt/v1/rbtv/" + asset
                                + "/vod/fmp4/" + locale + "/0/1/CC.vtt"
                                + "?category=personal_computer&os_family=http&device_group=group_3"));
                if (locale.toLowerCase(Locale.ROOT).startsWith("en")) {
                    break;
                }
            }
        }
        return new Clip(id, text(field(root, "title")), id.isEmpty() ? "" : page(id),
                text(field(root, "poster")), media, captions);
    }

    private static boolean playable(String id) {
        return id.contains("content:videos:") || id.contains("content:films:")
                || id.contains("content:episode-videos:");
    }

    private static String thumb(Object item) {
        String url = text(field(field(field(item, "media"), "mainImage"), "imageURL"));
        if (url.isEmpty()) {
            url = text(field(field(item, "image"), "imageURL"));
        }
        return url.replace("{op}", "c_fill,w_640,h_360,g_auto,q_auto,f_auto");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String decode(String value) {
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
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
                return null;
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
