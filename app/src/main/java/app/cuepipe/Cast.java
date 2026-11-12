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
 * shortcut: only the first 40 episodes are kept.
 */
public final class Cast {
    private static final int LIMIT = 40;

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

        public Episode(String title, String page, String audio, List<Caption> captions) {
            this.title = title == null ? "" : title;
            this.page = page == null ? "" : page;
            this.audio = audio == null ? "" : audio;
            this.captions = captions;
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

    private Cast() {
    }

    public static Show parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        Element root = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)))
                .getDocumentElement();
        Element channel = first(root, "channel");
        Element scope = channel == null ? root : channel;
        String title = text(first(scope, "title"));
        List<Episode> episodes = new ArrayList<>();
        NodeList nodes = scope.getChildNodes();
        for (int i = 0; i < nodes.getLength() && episodes.size() < LIMIT; i++) {
            if (!local(nodes.item(i)).equals("item")) {
                continue;
            }
            Element item = (Element) nodes.item(i);
            String audio = attr(first(item, "enclosure"), "url");
            if (audio.isEmpty()) {
                continue;
            }
            String link = text(first(item, "link"));
            String guid = text(first(item, "guid"));
            String page = !link.isEmpty() ? link : (guid.startsWith("http") ? guid : audio);
            List<Caption> captions = new ArrayList<>();
            for (Element transcript : named(item, "transcript")) {
                String url = attr(transcript, "url");
                if (!url.isEmpty()) {
                    captions.add(new Caption(url, attr(transcript, "type"), attr(transcript, "language")));
                }
            }
            episodes.add(new Episode(text(first(item, "title")), page, audio, captions));
        }
        return new Show(title, episodes);
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
