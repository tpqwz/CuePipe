package app.cuepipe;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * NASA+, learned from the Grayjay plugin.
 * Videos come from the WordPress route {@code /wp-json/wp/v2/video}.
 * The file is the HLS url in {@code meta.video-url}.
 */
public final class Nasa {
    private static final String LIST =
            "https://plus.nasa.gov/wp-json/wp/v2/video?per_page=20&orderby=date&order=desc";

    public static final class Clip {
        public final String title;
        public final String page;
        public final String thumb;
        public final String media;

        Clip(String title, String page, String thumb, String media) {
            this.title = title;
            this.page = page;
            this.thumb = thumb;
            this.media = media;
        }
    }

    private Nasa() {
    }

    public static String homeUrl() {
        return LIST + "&page=1";
    }

    public static String searchUrl(String query) {
        return LIST + "&page=1&search=" + encode(query);
    }

    public static String slugUrl(String slug) {
        return "https://plus.nasa.gov/wp-json/wp/v2/video?slug=" + encode(slug);
    }

    public static String slug(String url) {
        if (url == null) {
            return null;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        int at = lower.indexOf("plus.nasa.gov/video/");
        if (at < 0) {
            return null;
        }
        String rest = url.substring(at + "plus.nasa.gov/video/".length());
        int end = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        String slug = rest.substring(0, end);
        return slug.isEmpty() ? null : slug;
    }

    public static List<Clip> videos(String json) {
        Object root = parse(json);
        if (root instanceof Map) {
            root = field(root, "body");
        }
        List<Clip> out = new ArrayList<>();
        if (!(root instanceof List)) {
            return out;
        }
        for (Object item : (List<?>) root) {
            Clip clip = clip(item);
            if (!clip.page.isEmpty()) {
                out.add(clip);
            }
        }
        return out;
    }

    public static Clip one(String json) {
        List<Clip> clips = videos(json);
        return clips.isEmpty() ? new Clip("", "", "", "") : clips.get(0);
    }

    private static Clip clip(Object item) {
        Object meta = field(item, "meta");
        String title = plain(text(field(field(item, "title"), "rendered")));
        if (title.isEmpty()) {
            title = plain(text(field(item, "title")));
        }
        return new Clip(
                title,
                text(field(item, "link")),
                text(field(meta, "featured-image")),
                text(field(meta, "video-url")));
    }

    private static String plain(String value) {
        String text = value.replaceAll("<[^>]+>", " ");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                int semi = text.indexOf(';', i);
                if (semi > i && semi - i < 10) {
                    String entity = text.substring(i + 1, semi);
                    if (entity.startsWith("#") && entity.length() > 1) {
                        try {
                            int code = entity.startsWith("#x") || entity.startsWith("#X")
                                    ? Integer.parseInt(entity.substring(2), 16)
                                    : Integer.parseInt(entity.substring(1));
                            out.append((char) code);
                            i = semi;
                            continue;
                        } catch (NumberFormatException ignored) {
                            // Keep the raw ampersand.
                        }
                    } else if ("amp".equals(entity)) {
                        out.append('&');
                        i = semi;
                        continue;
                    } else if ("quot".equals(entity)) {
                        out.append('"');
                        i = semi;
                        continue;
                    }
                }
            }
            out.append(c);
        }
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
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
