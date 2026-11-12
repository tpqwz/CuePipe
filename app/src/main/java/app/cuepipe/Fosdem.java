package app.cuepipe;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FOSDEM, learned from the Grayjay plugin.
 * Talks come from api.opensource-tv.com. The file is the mp4 mirror.
 * A talk can include a WebVTT track.
 */
public final class Fosdem {
    public static final String HOME = "https://api.opensource-tv.com/events?page=1&pageSize=20";
    private static final Pattern SCHEDULE = Pattern.compile(
            "fosdem\\.org/(\\d{4})/schedule/event/([^/?#]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILE = Pattern.compile(
            "video\\.fosdem\\.org/(\\d{4})/[^/?#]+/([^/?#]+?)(?:\\.av1)?\\.(?:mp4|webm|vtt)",
            Pattern.CASE_INSENSITIVE);

    public static final class Caption {
        public final String lang;
        public final String url;
        public final String label;

        Caption(String lang, String url, String label) {
            this.lang = lang;
            this.url = url;
            this.label = label;
        }
    }

    public static final class Talk {
        public final String title;
        public final String page;
        public final String thumb;
        public final String speaker;
        public final String media;
        public final String year;
        public final String slug;
        public final List<Caption> captions;

        Talk(String title, String page, String thumb, String speaker, String media,
                String year, String slug, List<Caption> captions) {
            this.title = title;
            this.page = page;
            this.thumb = thumb;
            this.speaker = speaker;
            this.media = media;
            this.year = year;
            this.slug = slug;
            this.captions = captions;
        }
    }

    private Fosdem() {
    }

    public static String searchUrl(String query) {
        return "https://api.opensource-tv.com/search?page=1&pageSize=20&q=" + encode(query);
    }

    public static String eventUrl(String year, String slug) {
        return "https://api.opensource-tv.com/events/" + year + "/" + encode(slug);
    }

    /** Year and slug from a schedule page or a video.fosdem.org file. */
    public static String[] key(String url) {
        if (url == null) {
            return null;
        }
        Matcher schedule = SCHEDULE.matcher(url);
        if (schedule.find()) {
            return new String[] {schedule.group(1), schedule.group(2)};
        }
        Matcher file = FILE.matcher(url);
        if (file.find()) {
            return new String[] {file.group(1), file.group(2)};
        }
        return null;
    }

    public static List<Talk> talks(String json) {
        List<Talk> out = new ArrayList<>();
        Object items = field(parse(json), "items");
        if (!(items instanceof List)) {
            Talk one = talk(parse(json));
            if (!one.media.isEmpty()) {
                out.add(one);
            }
            return out;
        }
        for (Object item : (List<?>) items) {
            Talk talk = talk(item);
            if (!talk.media.isEmpty() && !talk.page.isEmpty()) {
                out.add(talk);
            }
        }
        return out;
    }

    public static Talk event(String json) {
        return talk(parse(json));
    }

    private static Talk talk(Object node) {
        String year = text(field(node, "year"));
        String slug = text(field(node, "slug"));
        List<Caption> captions = new ArrayList<>();
        Object subs = field(node, "subtitles");
        if (subs instanceof List) {
            for (Object sub : (List<?>) subs) {
                String url = text(field(sub, "url"));
                if (url.isEmpty()) {
                    continue;
                }
                String lang = text(field(sub, "language"));
                captions.add(new Caption(
                        lang.isEmpty() ? "en" : lang,
                        url,
                        text(field(sub, "label"))));
            }
        }
        return new Talk(
                text(field(node, "title")),
                text(field(node, "url")),
                text(field(node, "thumbnailUrl")),
                speakers(field(node, "speakers")),
                text(field(node, "mp4Url")),
                year,
                slug,
                captions);
    }

    private static String speakers(Object node) {
        if (!(node instanceof List)) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Object speaker : (List<?>) node) {
            String name = text(field(speaker, "name"));
            if (name.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(name);
        }
        return out.toString();
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
        if (value instanceof String) {
            return (String) value;
        }
        return value == null ? "" : String.valueOf(value);
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
            String number = text.substring(start, at);
            int dot = number.indexOf('.');
            return dot < 0 ? number : number.substring(0, dot);
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
