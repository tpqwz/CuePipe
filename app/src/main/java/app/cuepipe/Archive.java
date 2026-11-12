package app.cuepipe;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Internet Archive, learned from the Grayjay plugin.
 * Home and search use advancedsearch. A playable file is
 * {@code https://archive.org/download/{id}/{name}}.
 */
public final class Archive {
    private static final String SEARCH = "https://archive.org/advancedsearch.php?output=json&rows=20&page=1";
    private static final String META = "https://archive.org/metadata/";
    private static final String DOWNLOAD = "https://archive.org/download/";
    private static final String PAGE = "https://archive.org/details/";
    private static final String THUMB = "https://archive.org/services/img/";

    public static final class Caption {
        public final String lang;
        public final String url;

        Caption(String lang, String url) {
            this.lang = lang;
            this.url = url;
        }
    }

    public static final class Item {
        public final String id;
        public final String title;
        public final String creator;
        public final String page;
        public final String thumb;
        public final String media;
        public final List<Caption> captions;

        Item(String id, String title, String creator, String page, String thumb, String media,
                List<Caption> captions) {
            this.id = id;
            this.title = title;
            this.creator = creator;
            this.page = page;
            this.thumb = thumb;
            this.media = media;
            this.captions = captions;
        }
    }

    private Archive() {
    }

    public static String homeUrl() {
        return SEARCH + "&sort[]=downloads+desc&fl[]=identifier&fl[]=title&fl[]=creator&q="
                + encode("mediatype:(movies OR video)");
    }

    public static String searchUrl(String query) {
        String term = query == null ? "" : query.replace("\"", "").trim();
        String q = "(title:(\"" + term + "\") OR subject:(\"" + term + "\"))"
                + " AND mediatype:(movies OR video)";
        return SEARCH + "&fl[]=identifier&fl[]=title&fl[]=creator&q=" + encode(q);
    }

    public static String metadataUrl(String id) {
        return META + encode(id);
    }

    /** Identifier from a details or download link. */
    public static String identifier(String url) {
        if (url == null) {
            return null;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        int at = lower.indexOf("archive.org/details/");
        int mark = "archive.org/details/".length();
        if (at < 0) {
            at = lower.indexOf("archive.org/download/");
            mark = "archive.org/download/".length();
        }
        if (at < 0) {
            return null;
        }
        String rest = url.substring(at + mark);
        int end = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        String id = rest.substring(0, end);
        return id.isEmpty() ? null : id;
    }

    public static List<Item> docs(String json) {
        List<Item> out = new ArrayList<>();
        Object docs = field(field(parse(json), "response"), "docs");
        if (!(docs instanceof List)) {
            return out;
        }
        for (Object doc : (List<?>) docs) {
            String id = text(field(doc, "identifier"));
            if (id.isEmpty()) {
                continue;
            }
            out.add(new Item(
                    id,
                    text(field(doc, "title")),
                    names(field(doc, "creator")),
                    PAGE + id,
                    THUMB + id,
                    "",
                    List.of()));
        }
        return out;
    }

    /** Picks one video file. A negative height means 720. Zero means the lowest. */
    public static Item item(String json, int targetHeight) {
        Object root = parse(json);
        Object metadata = field(root, "metadata");
        String id = text(field(metadata, "identifier"));
        if (id.isEmpty()) {
            id = text(field(root, "identifier"));
        }
        String title = names(field(metadata, "title"));
        String creator = names(field(metadata, "creator"));
        Object files = field(root, "files");
        String media = "";
        int bestGap = Integer.MAX_VALUE;
        int bestHeight = Integer.MAX_VALUE;
        List<Caption> captions = new ArrayList<>();
        if (files instanceof List) {
            for (Object file : (List<?>) files) {
                String name = text(field(file, "name"));
                if (name.isEmpty() || "metadata".equals(text(field(file, "source")))) {
                    continue;
                }
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".srt") || lower.endsWith(".vtt")) {
                    if (captions.size() < 4) {
                        captions.add(new Caption(lang(lower), DOWNLOAD + id + "/" + encodePath(name) + "?cnt=0"));
                    }
                    continue;
                }
                if (!video(file, lower)) {
                    continue;
                }
                int height = number(text(field(file, "height")));
                int gap = targetHeight == 0
                        ? (height <= 0 ? 10000 : height)
                        : Math.abs((height <= 0 ? 480 : height) - (targetHeight < 0 ? 720 : targetHeight));
                if (media.isEmpty() || gap < bestGap || (gap == bestGap && height > 0 && height < bestHeight)) {
                    media = DOWNLOAD + id + "/" + encodePath(name);
                    bestGap = gap;
                    bestHeight = height <= 0 ? Integer.MAX_VALUE : height;
                }
            }
        }
        return new Item(id, title, creator, id.isEmpty() ? "" : PAGE + id, id.isEmpty() ? "" : THUMB + id,
                media, captions);
    }

    private static boolean video(Object file, String lowerName) {
        if (!(lowerName.endsWith(".mp4") || lowerName.endsWith(".m4v")
                || lowerName.endsWith(".webm") || lowerName.endsWith(".ogv"))) {
            return false;
        }
        String format = text(field(file, "format")).toLowerCase(Locale.ROOT);
        String blob = format + " " + lowerName;
        return blob.contains("mp4") || blob.contains("h.264") || blob.contains("mpeg4")
                || blob.contains("webm") || blob.contains("ogg") || blob.contains("ogv");
    }

    private static String lang(String name) {
        if (name.contains(".en.") || name.endsWith(".en.srt") || name.endsWith(".en.vtt")) {
            return "en";
        }
        if (name.contains(".asr.")) {
            return "en";
        }
        return "und";
    }

    private static String names(Object value) {
        if (value instanceof List) {
            StringBuilder out = new StringBuilder();
            for (Object part : (List<?>) value) {
                String text = text(part);
                if (text.isEmpty()) {
                    continue;
                }
                if (out.length() > 0) {
                    out.append(", ");
                }
                out.append(text);
            }
            return out.toString();
        }
        return text(value);
    }

    private static int number(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Encode each path segment so a slash inside a filename stays a slash. */
    private static String encodePath(String name) {
        String[] parts = name.split("/");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                out.append('/');
            }
            out.append(encode(parts[i]));
        }
        return out.toString();
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
