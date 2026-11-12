package app.cuepipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * TED Talks, learned from the Grayjay plugin.
 * Home and a talk come from www.ted.com/graphql. Search is www.ted.com/api/search.
 * Medium is 480p, high is 1080p. Captions are the WebVTT files named in the HLS metadata.
 */
public final class Ted {
    public static final String GRAPHQL = "https://www.ted.com/graphql";
    public static final String SEARCH = "https://www.ted.com/api/search";
    private static final String PAGE = "https://www.ted.com/talks/";

    public static final class Caption {
        public final String lang;
        public final String url;

        Caption(String lang, String url) {
            this.lang = lang;
            this.url = url;
        }
    }

    public static final class Talk {
        public final String slug;
        public final String title;
        public final String page;
        public final String speaker;
        public final String thumb;
        public final String low;
        public final String medium;
        public final String high;
        public final String audio;
        public final String hls;
        public final String metadata;
        public final List<Caption> captions;

        Talk(String slug, String title, String page, String speaker, String thumb,
                String low, String medium, String high, String audio, String hls,
                String metadata, List<Caption> captions) {
            this.slug = slug;
            this.title = title;
            this.page = page;
            this.speaker = speaker;
            this.thumb = thumb;
            this.low = low;
            this.medium = medium;
            this.high = high;
            this.audio = audio;
            this.hls = hls;
            this.metadata = metadata;
            this.captions = captions;
        }
    }

    private Ted() {
    }

    public static String homeBody() {
        return "{\"query\":" + quote(
                "query Videos($first: Int) { videos(first: $first) { nodes { title slug canonicalUrl "
                        + "speakers { nodes { firstname lastname photoUrl } } } } }")
                + ",\"variables\":{\"first\":20}}";
    }

    public static String detailsBody(String slug) {
        return "{\"operationName\":\"shareLinks\",\"query\":" + quote(
                "query shareLinks($slug: String!, $language: String) { videos(slug: [$slug], language: $language, "
                        + "first: 1, isPublished: [true, false], channel: ALL) { nodes { title slug canonicalUrl "
                        + "hlsUrl audioDownload playerData nativeDownloads { low medium high } "
                        + "speakers { nodes { firstname lastname photoUrl } } } } }")
                + ",\"variables\":{\"slug\":" + quote(slug) + ",\"language\":\"en\"}}";
    }

    public static String searchBody(String query) {
        String term = query == null ? "" : query;
        return "[{\"indexName\":\"newest\",\"params\":{\"query\":" + quote(term)
                + ",\"hitsPerPage\":20,\"page\":0}}]";
    }

    public static String slug(String url) {
        if (url == null) {
            return null;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        int at = lower.indexOf("ted.com/talks/");
        if (at < 0) {
            return null;
        }
        String rest = url.substring(at + "ted.com/talks/".length());
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

    public static List<Talk> home(String json) {
        return nodes(field(field(field(parse(json), "data"), "videos"), "nodes"), false);
    }

    public static Talk details(String json) {
        List<Talk> talks = nodes(field(field(field(parse(json), "data"), "videos"), "nodes"), true);
        return talks.isEmpty() ? blank() : talks.get(0);
    }

    public static List<Talk> hits(String json) {
        List<Talk> out = new ArrayList<>();
        Object results = field(parse(json), "results");
        if (!(results instanceof List) || ((List<?>) results).isEmpty()) {
            return out;
        }
        Object hits = field(((List<?>) results).get(0), "hits");
        if (!(hits instanceof List)) {
            return out;
        }
        for (Object hit : (List<?>) hits) {
            String slug = text(field(hit, "slug"));
            if (slug.isEmpty()) {
                continue;
            }
            out.add(new Talk(
                    slug,
                    text(field(hit, "title")),
                    PAGE + slug,
                    text(field(hit, "speakers")),
                    photo(field(hit, "photos")),
                    "", "", "", "", "", "",
                    List.of()));
        }
        return out;
    }

    /** Captions listed in the HLS metadata document. */
    public static List<Caption> captions(String metadataJson) {
        List<Caption> out = new ArrayList<>();
        Object subs = field(parse(metadataJson), "subtitles");
        if (!(subs instanceof List)) {
            return out;
        }
        for (Object sub : (List<?>) subs) {
            String url = text(field(sub, "webvtt"));
            if (url.isEmpty() || out.size() >= 4) {
                continue;
            }
            String lang = text(field(sub, "code"));
            out.add(new Caption(lang.isEmpty() ? "en" : lang, url));
        }
        return out;
    }

    /**
     * 360 uses the low file, 720 the medium file, and auto the high file.
     * Zero prefers the audio download.
     */
    public static String choose(Talk talk, int targetHeight) {
        if (talk == null) {
            return "";
        }
        if (targetHeight == 0 && !talk.audio.isEmpty()) {
            return talk.audio;
        }
        if (targetHeight == 360) {
            return first(talk.low, talk.medium, talk.high, talk.hls);
        }
        if (targetHeight == 720) {
            return first(talk.medium, talk.high, talk.low, talk.hls);
        }
        return first(talk.high, talk.medium, talk.low, talk.hls);
    }

    private static List<Talk> nodes(Object nodes, boolean withMedia) {
        List<Talk> out = new ArrayList<>();
        if (!(nodes instanceof List)) {
            return out;
        }
        for (Object node : (List<?>) nodes) {
            String slug = text(field(node, "slug"));
            if (slug.isEmpty()) {
                continue;
            }
            String page = text(field(node, "canonicalUrl"));
            if (page.isEmpty()) {
                page = PAGE + slug;
            }
            Object downloads = field(node, "nativeDownloads");
            String player = text(field(node, "playerData"));
            String metadata = "";
            if (!player.isEmpty()) {
                Object resources = field(parse(player), "resources");
                metadata = text(field(field(resources, "hls"), "metadata"));
            }
            out.add(new Talk(
                    slug,
                    text(field(node, "title")),
                    page,
                    speaker(field(node, "speakers")),
                    speakerPhoto(field(node, "speakers")),
                    text(field(downloads, "low")),
                    text(field(downloads, "medium")),
                    text(field(downloads, "high")),
                    text(field(node, "audioDownload")),
                    text(field(node, "hlsUrl")),
                    metadata,
                    List.of()));
            if (!withMedia) {
                continue;
            }
        }
        return out;
    }

    private static String speaker(Object speakers) {
        Object nodes = field(speakers, "nodes");
        if (!(nodes instanceof List) || ((List<?>) nodes).isEmpty()) {
            return "";
        }
        Object node = ((List<?>) nodes).get(0);
        String name = (text(field(node, "firstname")) + " " + text(field(node, "lastname"))).trim();
        return name;
    }

    private static String speakerPhoto(Object speakers) {
        Object nodes = field(speakers, "nodes");
        if (!(nodes instanceof List) || ((List<?>) nodes).isEmpty()) {
            return "";
        }
        return text(field(((List<?>) nodes).get(0), "photoUrl"));
    }

    private static String photo(Object photos) {
        if (!(photos instanceof List) || ((List<?>) photos).isEmpty()) {
            return "";
        }
        Object sizes = field(((List<?>) photos).get(0), "photo_sizes");
        if (!(sizes instanceof List) || ((List<?>) sizes).isEmpty()) {
            return "";
        }
        return text(field(((List<?>) sizes).get(0), "url"));
    }

    private static String first(String... urls) {
        for (String url : urls) {
            if (url != null && !url.isEmpty()) {
                return url;
            }
        }
        return "";
    }

    private static Talk blank() {
        return new Talk("", "", "", "", "", "", "", "", "", "", "", List.of());
    }

    private static String quote(String raw) {
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
