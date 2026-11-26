package app.cuepipe;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * A podcast RSS feed. Podcast 2.0 transcripts are caption files on an episode.
 * The screen shows {@link #PAGE} episodes at a time.
 */
public final class Cast {
    public static final int PAGE = 40;

    public static final class Caption {
        public final String url;
        public final String type;
        public final String lang;

        public Caption(String url, String type, String lang) {
            this.url = url == null ? "" : url;
            this.type = type == null ? "" : type;
            this.lang = lang == null ? "" : lang;
        }
    }

    public static final class Episode {
        public final String title;
        public final String page;
        public final String audio;
        public final List<Caption> captions;
        public final String chapters;

        public Episode(String title, String page, String audio, List<Caption> captions,
                       String chapters) {
            this.title = title == null ? "" : title;
            this.page = page == null ? "" : page;
            this.audio = audio == null ? "" : audio;
            this.captions = captions;
            this.chapters = chapters == null ? "" : chapters;
        }
    }

    public static final class Show {
        public final String title;
        public final List<Episode> episodes;

        public Show(String title, List<Episode> episodes) {
            this.title = title == null ? "" : title;
            this.episodes = episodes;
        }
    }

    /** A blog or news item. It has a link and may have no audio. */
    public static final class Post {
        public final String title;
        public final String url;

        public Post(String title, String url) {
            this.title = title == null ? "" : title;
            this.url = url == null ? "" : url;
        }
    }

    private Cast() {
    }

    public static Show parse(String xml) throws Exception {
        Element scope = scope(xml);
        String title = text(first(scope, "title"));
        List<Episode> episodes = new ArrayList<>();
        NodeList nodes = scope.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            String kind = local(nodes.item(i));
            if (!kind.equals("item") && !kind.equals("entry")) {
                continue;
            }
            Element item = (Element) nodes.item(i);
            String audio = attr(first(item, "enclosure"), "url");
            if (audio.isEmpty()) {
                audio = alternateAudio(item);
            }
            if (audio.isEmpty()) {
                continue;
            }
            String link = linkOf(item);
            String guid = text(first(item, "guid"));
            String page = !link.isEmpty() ? link : (guid.startsWith("http") ? guid : audio);
            List<Caption> captions = new ArrayList<>();
            for (Element transcript : named(item, "transcript")) {
                String url = attr(transcript, "url");
                if (!url.isEmpty()) {
                    captions.add(new Caption(url, attr(transcript, "type"), attr(transcript, "language")));
                }
            }
            String chapters = "";
            for (Element chapter : named(item, "chapters")) {
                String url = attr(chapter, "url");
                if (!url.isEmpty()) {
                    chapters = url;
                    break;
                }
            }
            episodes.add(new Episode(text(first(item, "title")), page, audio, captions, chapters));
        }
        return new Show(title, episodes);
    }

    public static String feedTitle(String xml) throws Exception {
        String title = text(first(scope(xml), "title"));
        return title;
    }

    /** Every item with a link, including ones that have no enclosure. */
    public static List<Post> posts(String xml) throws Exception {
        Element scope = scope(xml);
        List<Post> posts = new ArrayList<>();
        NodeList nodes = scope.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            String kind = local(nodes.item(i));
            if (!kind.equals("item") && !kind.equals("entry")) {
                continue;
            }
            Element item = (Element) nodes.item(i);
            String url = linkOf(item);
            if (url.isEmpty()) {
                String guid = text(first(item, "guid"));
                url = guid.startsWith("http") ? guid : "";
            }
            if (url.isEmpty()) {
                continue;
            }
            String title = text(first(item, "title"));
            posts.add(new Post(title.isEmpty() ? url : title, url));
        }
        return posts;
    }

    /** One note that holds every followed feed. Each row is service, name, url. */
    public static String opml(List<String[]> feeds) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<opml version=\"2.0\"><head><title>CuePipe</title></head><body>\n");
        for (String[] feed : feeds) {
            if (feed == null || feed.length < 3 || feed[2] == null || feed[2].isEmpty()) {
                continue;
            }
            sb.append("<outline type=\"").append(xml(feed[0]))
                    .append("\" text=\"").append(xml(feed[1]))
                    .append("\" xmlUrl=\"").append(xml(feed[2])).append("\"/>\n");
        }
        sb.append("</body></opml>\n");
        return sb.toString();
    }

    /** Feeds named in an OPML note. Each row is service, name, url. */
    public static List<String[]> outlines(String xml) throws Exception {
        List<String[]> out = new ArrayList<>();
        walkOutlines(root(xml), out);
        return out;
    }

    private static void walkOutlines(Element el, List<String[]> out) {
        if ("outline".equals(local(el))) {
            String url = attr(el, "xmlUrl");
            if (url.isEmpty()) {
                url = attr(el, "url");
            }
            if (!url.isEmpty()) {
                String type = attr(el, "type").toLowerCase(java.util.Locale.ROOT);
                String service = type.contains("podcast") ? "Podcast" : "RSS";
                String name = attr(el, "text");
                if (name.isEmpty()) {
                    name = attr(el, "title");
                }
                out.add(new String[] {service, name.isEmpty() ? url : name, url});
            }
        }
        NodeList nodes = el.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element) {
                walkOutlines((Element) nodes.item(i), out);
            }
        }
    }

    private static String xml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static Element root(String xml) throws Exception {
        if (xml != null && xml.toLowerCase(java.util.Locale.ROOT).contains("<!doctype")) {
            throw new IllegalArgumentException("This feed is not a podcast");
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setExpandEntityReferences(false);
        // Android's parser throws if the Apache doctype feature is set.
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (javax.xml.parsers.ParserConfigurationException ignored) {
        }
        try {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (javax.xml.parsers.ParserConfigurationException ignored) {
        }
        return factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml == null ? "" : xml)))
                .getDocumentElement();
    }

    private static Element scope(String xml) throws Exception {
        Element root = root(xml);
        Element channel = first(root, "channel");
        return channel == null ? root : channel;
    }

    private static String linkOf(Element item) {
        String link = text(first(item, "link"));
        if (!link.isEmpty()) {
            return link;
        }
        return attr(first(item, "link"), "href");
    }

    /** The first podcast:alternateEnclosure source, when the item has no enclosure. */
    private static String alternateAudio(Element item) {
        for (Element enclosure : named(item, "alternateEnclosure")) {
            for (Element source : named(enclosure, "source")) {
                String uri = attr(source, "uri");
                if (!uri.isEmpty()) {
                    return uri;
                }
            }
        }
        return "";
    }

    private static List<Element> named(Element parent, String want) {
        List<Element> out = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (want.equals(local(nodes.item(i)))) {
                out.add((Element) nodes.item(i));
            }
        }
        return out;
    }

    private static Element first(Element parent, String want) {
        if (parent == null) {
            return null;
        }
        List<Element> found = named(parent, want);
        return found.isEmpty() ? null : found.get(0);
    }

    private static String local(Node node) {
        if (!(node instanceof Element)) {
            return "";
        }
        String name = ((Element) node).getLocalName();
        if (name == null || name.isEmpty()) {
            name = node.getNodeName();
        }
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    private static String text(Element element) {
        return element == null ? "" : element.getTextContent().trim();
    }

    private static String attr(Element element, String name) {
        if (element == null || !element.hasAttribute(name)) {
            return "";
        }
        return element.getAttribute(name).trim();
    }
}
