package app.cuepipe;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Caption text becomes a transcript body and the spans that seek inside it.
 * A span's byteStart is a UTF-8 offset into body, which is what FTS4 offsets() returns.
 */
public final class Cues {
    public static final class Span {
        public final int t0;
        public final int t1;
        public final int byteStart;
        public final int byteLen;
        public final String text;

        public Span(int t0, int t1, int byteStart, int byteLen, String text) {
            this.t0 = t0;
            this.t1 = t1;
            this.byteStart = byteStart;
            this.byteLen = byteLen;
            this.text = text;
        }
    }

    public static final class Transcript {
        public final String source;
        public final String body;
        public final List<Span> spans;

        public Transcript(String source, String body, List<Span> spans) {
            this.source = source;
            this.body = body;
            this.spans = spans;
        }
    }

    private static final class Cue {
        final int t0;
        final int t1;
        final String text;

        Cue(int t0, int t1, String text) {
            this.t0 = t0;
            this.t1 = t1;
            this.text = text;
        }
    }

    private Cues() {
    }

    /** format is a suffix such as vtt, srt, ttml, or srv3. Null sniffs the body. */
    public static Transcript parse(String raw, String format, boolean auto) {
        if (raw == null) {
            raw = "";
        }
        String kind = format == null ? "" : format.toLowerCase(Locale.US);
        if (kind.isEmpty()) {
            kind = sniff(raw);
        }
        List<Cue> cues;
        if (raw.startsWith("{") || raw.contains("\"tStartMs\"")) {
            cues = json3(raw);
        } else if ("vtt".equals(kind)) {
            cues = timed(raw);
        } else if ("srt".equals(kind)) {
            cues = timed(raw);
        } else if ("ttml".equals(kind) || kind.startsWith("srv") || raw.contains("<p")) {
            cues = xml(raw);
        } else {
            cues = timed(raw);
        }
        return pack(roll(cues), auto);
    }

    /** Index of the span with the greatest overlap of [t0, t1), or -1. */
    public static int overlapping(List<Span> spans, int t0, int t1) {
        int best = -1;
        int bestOverlap = 0;
        if (spans == null) {
            return -1;
        }
        for (int i = 0; i < spans.size(); i++) {
            Span s = spans.get(i);
            int overlap = Math.min(t1, s.t1) - Math.max(t0, s.t0);
            if (overlap > bestOverlap) {
                bestOverlap = overlap;
                best = i;
            }
        }
        return best;
    }

    /**
     * The span that contains timeMs. In a gap, the latest span that has already
     * started, so the reader stays on the line the playhead just left. Before the
     * first span, -1.
     */
    public static int indexAt(List<Span> spans, int timeMs) {
        int at = -1;
        if (spans == null) {
            return -1;
        }
        for (int i = 0; i < spans.size(); i++) {
            Span span = spans.get(i);
            if (span.t0 > timeMs) {
                break;
            }
            at = i;
            if (timeMs < span.t1) {
                return i;
            }
        }
        return at;
    }

    /** The primary line that contains timeMs, plus each extra line that overlaps it. */
    public static String captionAt(List<Span> primary, List<List<Span>> extras, int timeMs) {
        int at = -1;
        if (primary != null) {
            for (int i = 0; i < primary.size(); i++) {
                Span s = primary.get(i);
                if (timeMs >= s.t0 && timeMs < s.t1) {
                    at = i;
                    break;
                }
            }
        }
        if (at < 0) {
            return "";
        }
        Span span = primary.get(at);
        StringBuilder text = new StringBuilder(span.text);
        if (extras != null) {
            for (List<Span> extra : extras) {
                int other = overlapping(extra, span.t0, span.t1);
                if (other >= 0) {
                    text.append('\n').append(extra.get(other).text);
                }
            }
        }
        return text.toString();
    }

    public static int spanIndex(List<Span> spans, int byteOffset) {
        for (int i = 0; i < spans.size(); i++) {
            Span s = spans.get(i);
            if (byteOffset >= s.byteStart && byteOffset < s.byteStart + s.byteLen) {
                return i;
            }
        }
        return -1;
    }

    /** First byte start from an FTS4 offsets() string, or -1. */
    public static int firstOffset(String offsets) {
        if (offsets == null) {
            return -1;
        }
        String[] p = offsets.trim().split("\\s+");
        if (p.length < 4 || p[0].isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(p[2]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String sniff(String raw) {
        int i = 0;
        while (i < raw.length() && Character.isWhitespace(raw.charAt(i))) {
            i++;
        }
        String head = raw.substring(i);
        if (head.startsWith("WEBVTT")) {
            return "vtt";
        }
        if (head.startsWith("{") || head.contains("\"tStartMs\"")) {
            return "srv3";
        }
        if (head.contains("<tt") || head.contains("<timedtext")) {
            return "ttml";
        }
        return "srt";
    }

    private static List<Cue> timed(String raw) {
        List<Cue> out = new ArrayList<>();
        String[] blocks = raw.replace("\r\n", "\n").replace('\r', '\n').split("\n\n+");
        for (String block : blocks) {
            String[] lines = block.split("\n");
            int arrow = -1;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains("-->")) {
                    arrow = i;
                    break;
                }
            }
            if (arrow < 0) {
                continue;
            }
            String[] stamps = lines[arrow].split("-->");
            if (stamps.length < 2) {
                continue;
            }
            int t0 = clock(stamps[0]);
            int t1 = clock(stamps[1]);
            if (t0 < 0 || t1 < 0) {
                continue;
            }
            StringBuilder text = new StringBuilder();
            for (int i = arrow + 1; i < lines.length; i++) {
                if (text.length() > 0) {
                    text.append(' ');
                }
                text.append(lines[i]);
            }
            String clean = clean(text.toString());
            if (!clean.isEmpty()) {
                out.add(new Cue(t0, Math.max(t1, t0), clean));
            }
        }
        return out;
    }

    private static List<Cue> json3(String raw) {
        List<Cue> out = new ArrayList<>();
        int i = 0;
        while (i < raw.length()) {
            int at = raw.indexOf("\"tStartMs\"", i);
            if (at < 0) {
                break;
            }
            int next = raw.indexOf("\"tStartMs\"", at + 10);
            String slice = next < 0 ? raw.substring(at) : raw.substring(at, next);
            int t0 = jsonNum(slice, "tStartMs");
            int dur = jsonNum(slice, "dDurationMs");
            String text = clean(jsonSegs(slice));
            if (t0 >= 0 && dur >= 0 && !text.isEmpty()) {
                out.add(new Cue(t0, t0 + dur, text));
            }
            i = at + 10;
        }
        return out;
    }

    private static int jsonNum(String slice, String key) {
        int at = slice.indexOf("\"" + key + "\"");
        if (at < 0) {
            return -1;
        }
        int colon = slice.indexOf(':', at);
        if (colon < 0) {
            return -1;
        }
        int j = colon + 1;
        while (j < slice.length() && Character.isWhitespace(slice.charAt(j))) {
            j++;
        }
        int k = j;
        while (k < slice.length() && Character.isDigit(slice.charAt(k))) {
            k++;
        }
        if (k == j) {
            return -1;
        }
        try {
            return Integer.parseInt(slice.substring(j, k));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String jsonSegs(String slice) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < slice.length()) {
            int at = slice.indexOf("\"utf8\"", i);
            if (at < 0) {
                break;
            }
            int colon = slice.indexOf(':', at);
            if (colon < 0) {
                break;
            }
            int q = colon + 1;
            while (q < slice.length() && Character.isWhitespace(slice.charAt(q))) {
                q++;
            }
            if (q >= slice.length() || slice.charAt(q) != '"') {
                i = at + 6;
                continue;
            }
            sb.append(jsonString(slice, q));
            i = q + 1;
        }
        return sb.toString();
    }

    private static String jsonString(String s, int openQuote) {
        StringBuilder sb = new StringBuilder();
        for (int i = openQuote + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\' || i + 1 >= s.length()) {
                sb.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'n':
                    sb.append('\n');
                    break;
                case 'r':
                    sb.append('\r');
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'u':
                    if (i + 4 < s.length()) {
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            sb.append(n);
                        }
                    }
                    break;
                default:
                    sb.append(n);
                    break;
            }
        }
        return sb.toString();
    }

    private static List<Cue> xml(String raw) {
        List<Cue> out = new ArrayList<>();
        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Android's parser rejects some of these features. A rejected feature
            // must not throw away the caption file.
            tryFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
            tryFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(true);
            doc = factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return out;
        }
        List<Element> ps = paragraphs(doc);
        int cursor = 0;
        for (Element el : ps) {
            String text = clean(textOf(el));
            if (text.isEmpty()) {
                continue;
            }
            int t0 = attrMs(el, "t", "begin");
            int dur = attrMs(el, "d", "dur");
            int t1 = attrMs(el, "end");
            if (t0 < 0) {
                t0 = cursor;
            }
            if (t1 < 0) {
                t1 = dur >= 0 ? t0 + dur : t0;
            }
            cursor = Math.max(t1, t0);
            out.add(new Cue(t0, cursor, text));
        }
        return out;
    }

    private static void tryFeature(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
        }
    }

    /** p elements, including a default-namespaced TTML p that getElementsByTagName misses. */
    private static List<Element> paragraphs(Document doc) {
        List<Element> out = new ArrayList<>();
        collectParagraphs(doc, out);
        return out;
    }

    private static void collectParagraphs(Node node, List<Element> out) {
        NodeList kids = node.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node child = kids.item(i);
            if (!(child instanceof Element)) {
                continue;
            }
            String name = child.getLocalName();
            if (name == null) {
                name = child.getNodeName();
            }
            int colon = name.indexOf(':');
            if (colon >= 0) {
                name = name.substring(colon + 1);
            }
            if ("p".equalsIgnoreCase(name)) {
                out.add((Element) child);
            }
            collectParagraphs(child, out);
        }
    }

    private static String textOf(Element el) {
        StringBuilder sb = new StringBuilder();
        NodeList kids = el.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n.getNodeType() == Node.TEXT_NODE) {
                sb.append(n.getNodeValue());
            } else if (n instanceof Element) {
                String name = n.getLocalName() == null ? n.getNodeName() : n.getLocalName();
                if ("br".equalsIgnoreCase(name)) {
                    sb.append(' ');
                } else {
                    sb.append(textOf((Element) n));
                }
            }
        }
        return sb.toString();
    }

    private static int attrMs(Element el, String... names) {
        for (String name : names) {
            String v = el.getAttribute(name);
            if (v != null && !v.isEmpty()) {
                return msValue(v);
            }
        }
        return -1;
    }

    private static int msValue(String v) {
        String s = v.trim();
        try {
            if (s.endsWith("ms")) {
                return (int) Double.parseDouble(s.substring(0, s.length() - 2).trim());
            }
            if (s.endsWith("s") && !s.contains(":")) {
                return (int) (Double.parseDouble(s.substring(0, s.length() - 1).trim()) * 1000);
            }
            if (s.indexOf(':') >= 0) {
                return clock(s);
            }
            return (int) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static int clock(String stamp) {
        String s = stamp.trim().replace(',', '.');
        int space = s.indexOf(' ');
        if (space > 0) {
            s = s.substring(0, space);
        }
        String[] parts = s.split(":");
        try {
            double sec;
            int h = 0;
            int m = 0;
            if (parts.length == 3) {
                h = Integer.parseInt(parts[0].trim());
                m = Integer.parseInt(parts[1].trim());
                sec = Double.parseDouble(parts[2].trim());
            } else if (parts.length == 2) {
                m = Integer.parseInt(parts[0].trim());
                sec = Double.parseDouble(parts[1].trim());
            } else {
                return -1;
            }
            return (int) ((h * 3600L + m * 60L) * 1000L + Math.round(sec * 1000.0));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String clean(String s) {
        String t = s.replaceAll("<[^>]+>", " ");
        t = t.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replace("&quot;", "\"");
        t = t.replace('\n', ' ').replace('\r', ' ');
        return t.replaceAll("\\s+", " ").trim();
    }

    /**
     * A cue whose text is contained in the next overlapping cue is the rolling prefix.
     * The kept cue takes the earlier start and the later end.
     * shortcut: a shifted window that shares only a tail stays two lines.
     */
    private static List<Cue> roll(List<Cue> in) {
        List<Cue> out = new ArrayList<>();
        for (Cue c : in) {
            if (c.text.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                Cue prev = out.get(out.size() - 1);
                boolean overlap = prev.t1 > c.t0;
                if (overlap && containsText(c.text, prev.text)) {
                    out.set(out.size() - 1, new Cue(prev.t0, Math.max(prev.t1, c.t1), c.text));
                    continue;
                }
                if (overlap && containsText(prev.text, c.text)) {
                    out.set(out.size() - 1, new Cue(prev.t0, Math.max(prev.t1, c.t1), prev.text));
                    continue;
                }
            }
            out.add(c);
        }
        return out;
    }

    private static boolean containsText(String hay, String needle) {
        return hay.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static Transcript pack(List<Cue> cues, boolean auto) {
        StringBuilder body = new StringBuilder();
        List<Span> spans = new ArrayList<>();
        for (Cue c : cues) {
            if (body.length() > 0) {
                body.append('\n');
            }
            int start = body.toString().getBytes(StandardCharsets.UTF_8).length;
            body.append(c.text);
            int end = body.toString().getBytes(StandardCharsets.UTF_8).length;
            spans.add(new Span(c.t0, c.t1, start, end - start, c.text));
        }
        return new Transcript(auto ? "auto" : "manual", body.toString(), spans);
    }
}
