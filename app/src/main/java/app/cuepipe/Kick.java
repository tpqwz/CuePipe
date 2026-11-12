package app.cuepipe;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Kick, learned from the Grayjay plugin.
 * Home and search are livestreams. A channel page's {@code playback_url} is the HLS file.
 * shortcut: saved videos are not opened. A channel that is not live has no stream.
 */
public final class Kick {
    public static final String HOME =
            "https://web.kick.com/api/v1/livestreams?limit=20&sort=featured_home&language=en";
    private static final String PAGE = "https://kick.com/";

    public static final class Clip {
        public final String slug;
        public final String title;
        public final String author;
        public final String thumb;
        public final String page;
        public final String playback;

        Clip(String slug, String title, String author, String thumb, String page, String playback) {
            this.slug = slug;
            this.title = title;
            this.author = author;
            this.thumb = thumb;
            this.page = page;
            this.playback = playback;
        }
    }

    private Kick() {
    }

    public static String searchUrl(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() > 100) {
            trimmed = trimmed.substring(0, 100);
        }
        return "https://search.kick.com/api/v1/search/enriched?query=" + encode(trimmed);
    }

    public static String channelUrl(String slug) {
        return "https://kick.com/api/v2/channels/" + slug;
    }

    /** A channel page such as {@code https://kick.com/xqc}. Video and API links are not channels. */
    public static String slug(String url) {
        if (url == null) {
            return null;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        int at = lower.indexOf("kick.com/");
        if (at < 0) {
            return null;
        }
        String rest = url.substring(at + "kick.com/".length());
        int cut = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                cut = i;
                break;
            }
        }
        String name = rest.substring(0, cut);
        if (name.isEmpty() || name.contains(".")
                || "video".equalsIgnoreCase(name) || "videos".equalsIgnoreCase(name)
                || "api".equalsIgnoreCase(name) || "categories".equalsIgnoreCase(name)
                || "search".equalsIgnoreCase(name)) {
            return null;
        }
        if (rest.length() > cut && rest.substring(cut).contains("/videos/")) {
            return null;
        }
        return name;
    }

    public static List<Clip> livestreams(String json) {
        List<Clip> out = new ArrayList<>();
        Object items = field(field(parse(json), "data"), "livestreams");
        if (!(items instanceof List)) {
            return out;
        }
        for (Object item : (List<?>) items) {
            Object channel = field(item, "channel");
            String slug = text(field(channel, "slug"));
            if (slug.isEmpty()) {
                slug = text(field(item, "slug"));
            }
            if (slug.isEmpty()) {
                continue;
            }
            String author = text(field(channel, "username"));
            if (author.isEmpty()) {
                author = slug;
            }
            out.add(new Clip(
                    slug,
                    text(field(item, "title")),
                    author,
                    text(field(field(item, "thumbnail"), "src")),
                    PAGE + slug,
                    ""));
        }
        return out;
    }

    public static Clip channel(String json) {
        Object root = parse(json);
        Object live = field(root, "livestream");
        Object user = field(root, "user");
        String slug = text(field(root, "slug"));
        String author = text(field(user, "username"));
        if (author.isEmpty()) {
            author = slug;
        }
        String title = text(field(live, "session_title"));
        if (title.isEmpty()) {
            title = author;
        }
        return new Clip(
                slug,
                title,
                author,
                text(field(user, "profile_pic")),
                slug.isEmpty() ? "" : PAGE + slug,
                text(field(root, "playback_url")));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
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
