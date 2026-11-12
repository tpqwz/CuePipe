package app.cuepipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Odysee, from odysee-api. A free stream is
 * {@code https://player.odycdn.com/v6/streams/{claim}/{sdHash6}.mp4}.
 * Members-only, unlisted, purchase, and rental claims need an edge token this client does not have.
 */
public final class Odysee {
    public static final String PROXY = "https://api.na-backend.odysee.com/api/v1/proxy?m=";
    private static final String PAGE = "https://odysee.com/";
    private static final String CDN = "https://player.odycdn.com/v6/streams/";

    public static final class Clip {
        public final String id;
        public final String title;
        public final String page;
        public final String thumb;
        public final String channel;
        public final String channelId;
        public final String channelPage;
        public final String media;

        Clip(String id, String title, String page, String thumb, String channel, String channelId,
                String channelPage, String media) {
            this.id = id;
            this.title = title;
            this.page = page;
            this.thumb = thumb;
            this.channel = channel;
            this.channelId = channelId;
            this.channelPage = channelPage;
            this.media = media;
        }
    }

    private Odysee() {
    }

    public static String popularBody() {
        return rpc("claim_search", "{\"claim_type\":[\"stream\"],\"stream_types\":[\"video\"],"
                + "\"page_size\":20,\"no_totals\":true,\"order_by\":[\"release_time\"],\"has_source\":true,"
                + "\"not_tags\":[\"c:unlisted\",\"c:members-only\",\"c:purchase\",\"c:rental\"]}");
    }

    public static String searchBody(String query) {
        return rpc("claim_search", "{\"claim_type\":[\"stream\"],\"stream_types\":[\"video\"],"
                + "\"page_size\":20,\"no_totals\":true,\"order_by\":[\"release_time\"],\"has_source\":true,"
                + "\"text\":" + quote(query)
                + ",\"not_tags\":[\"c:unlisted\",\"c:members-only\",\"c:purchase\",\"c:rental\"]}");
    }

    public static String channelBody(String channelId) {
        return rpc("claim_search", "{\"claim_type\":[\"stream\"],\"stream_types\":[\"video\"],"
                + "\"page_size\":20,\"no_totals\":true,\"order_by\":[\"release_time\"],\"has_source\":true,"
                + "\"channel_ids\":[" + quote(channelId) + "]}");
    }

    public static String resolveBody(String uri) {
        return rpc("resolve", "{\"urls\":[" + quote(uri) + "]}");
    }

    /** A watch link becomes an {@code lbry://} URI. A channel-only link returns null. */
    public static String videoUri(String url) {
        String path = path(url);
        if (path.isEmpty() || !path.contains("/")) {
            return null;
        }
        if (path.startsWith("$/")) {
            return null;
        }
        return "lbry://" + hashSegments(path);
    }

    /** {@code @name:id} with no video segment, as an {@code lbry://} URI. */
    public static String channelUri(String url) {
        String path = path(url);
        if (!path.startsWith("@") || path.contains("/")) {
            return null;
        }
        return "lbry://" + hashSegments(path);
    }

    /** {@code @name:id} with no video segment. */
    public static String channelId(String url) {
        String path = path(url);
        if (!path.startsWith("@") || path.contains("/")) {
            return null;
        }
        int colon = path.lastIndexOf(':');
        if (colon < 0 || colon == path.length() - 1) {
            return null;
        }
        return path.substring(colon + 1);
    }

    public static List<Clip> videos(String json) {
        List<Clip> out = new ArrayList<>();
        Object items = field(field(parse(json), "result"), "items");
        if (!(items instanceof List)) {
            return out;
        }
        for (Object item : (List<?>) items) {
            Clip clip = clip(item);
            if (!clip.id.isEmpty() && !clip.media.isEmpty()) {
                out.add(clip);
            }
        }
        return out;
    }

    public static Clip resolved(String json) {
        Object result = field(parse(json), "result");
        if (!(result instanceof Map)) {
            return clip(null);
        }
        for (Object value : ((Map<?, ?>) result).values()) {
            Clip clip = clip(value);
            if (!clip.id.isEmpty()) {
                return clip;
            }
        }
        return clip(null);
    }

    private static Clip clip(Object claim) {
        String id = text(field(claim, "claim_id"));
        String name = text(field(claim, "name"));
        Object value = field(claim, "value");
        Object source = field(value, "source");
        String sd = text(field(source, "sd_hash"));
        String media = "";
        if (!id.isEmpty() && sd.length() >= 6 && !restricted(value)) {
            media = CDN + id + "/" + sd.substring(0, 6) + ".mp4";
        }
        Object channel = field(claim, "signing_channel");
        String channelName = text(field(channel, "name"));
        String channelId = text(field(channel, "claim_id"));
        String page;
        if (!channelName.isEmpty() && !channelId.isEmpty() && !name.isEmpty() && !id.isEmpty()) {
            page = PAGE + channelName + ":" + channelId + "/" + name + ":" + id;
        } else if (!name.isEmpty() && !id.isEmpty()) {
            page = PAGE + name + ":" + id;
        } else {
            page = "";
        }
        String channelPage = channelName.isEmpty() || channelId.isEmpty()
                ? "" : PAGE + channelName + ":" + channelId;
        return new Clip(
                id,
                text(field(value, "title")),
                page,
                text(field(field(value, "thumbnail"), "url")),
                channelName.startsWith("@") ? channelName.substring(1) : channelName,
                channelId,
                channelPage,
                media);
    }

    private static boolean restricted(Object value) {
        Object tags = field(value, "tags");
        if (!(tags instanceof List)) {
            return false;
        }
        for (Object tag : (List<?>) tags) {
            String name = text(tag);
            if (name.equals("c:unlisted") || name.equals("c:members-only")
                    || name.equals("c:purchase") || name.equals("c:rental")
                    || name.startsWith("purchase:") || name.startsWith("rental:")
                    || name.startsWith("c:scheduled:")) {
                return true;
            }
        }
        return false;
    }

    private static String path(String url) {
        if (url == null) {
            return "";
        }
        String rest = url;
        int scheme = rest.indexOf("://");
        if (scheme >= 0) {
            int slash = rest.indexOf('/', scheme + 3);
            rest = slash < 0 ? "" : rest.substring(slash + 1);
        }
        int cut = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            if (rest.charAt(i) == '?' || rest.charAt(i) == '#') {
                cut = i;
                break;
            }
        }
        return rest.substring(0, cut);
    }

    private static String hashSegments(String path) {
        String[] parts = path.split("/");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                out.append('/');
            }
            String segment = parts[i];
            int colon = segment.lastIndexOf(':');
            if (colon > 0) {
                out.append(segment, 0, colon).append('#').append(segment.substring(colon + 1));
            } else {
                out.append(segment);
            }
        }
        return out.toString();
    }

    private static String rpc(String method, String params) {
        return "{\"jsonrpc\":\"2.0\",\"method\":" + quote(method) + ",\"params\":" + params + ",\"id\":1}";
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
