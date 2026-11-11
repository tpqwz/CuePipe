package app.cuepipe;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;

/** One page of videos, channels, and playlists from a NewPipeExtractor service. */
public final class Browse {
    public static final class Found {
        public final String kind;
        public final String name;
        public final String url;
        public final String meta;
        public final String thumb;

        public Found(String kind, String name, String url, String meta) {
            this(kind, name, url, meta, "");
        }

        public Found(String kind, String name, String url, String meta, String thumb) {
            this.kind = kind;
            this.name = name;
            this.url = url;
            this.meta = meta == null ? "" : meta;
            this.thumb = thumb == null ? "" : thumb;
        }
    }

    /** The open list and the cursor for its next page. */
    public static final class Session {
        public final List<Found> items;
        public final Page next;
        public final int serviceId;
        public final String mode;
        final SearchQueryHandler search;
        final String listUrl;
        final ListLinkHandler tab;

        Session(List<Found> items, Page next, int serviceId, String mode,
                SearchQueryHandler search, String listUrl, ListLinkHandler tab) {
            this.items = items;
            this.next = next;
            this.serviceId = serviceId;
            this.mode = mode;
            this.search = search;
            this.listUrl = listUrl;
            this.tab = tab;
        }
    }

    private Browse() {
    }

    public static Session feed(StreamingService service) throws Exception {
        // YouTube removed the combined Trending tab. The charts that replaced it
        // are music, movies, and gaming. Live is the fallback when those are absent.
        // KioskInfo.getInfo calls getName(), which NPEs on some kiosks, so the
        // item page is fetched directly.
        org.schabi.newpipe.extractor.kiosk.KioskList kiosks = service.getKioskList();
        java.util.Set<String> available = kiosks.getAvailableKiosks();
        List<String> ids = new ArrayList<>();
        for (String id : new String[] {
                "trending_music", "trending_movies_and_shows", "trending_gaming"}) {
            if (available.contains(id)) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            ids.add(kiosks.getDefaultKioskId());
        }
        List<Found> items = new ArrayList<>();
        String url = null;
        Page next = null;
        Exception last = null;
        boolean single = ids.size() == 1;
        for (String id : ids) {
            try {
                KioskExtractor extractor = kiosks.getExtractorById(id, null);
                extractor.fetchPage();
                ListExtractor.InfoItemsPage<?> page = extractor.getInitialPage();
                List<Found> found = fromItems(page.getItems());
                if (found.isEmpty()) {
                    continue;
                }
                items.addAll(found);
                if (url == null) {
                    url = extractor.getUrl();
                    if (single) {
                        next = page.getNextPage();
                    }
                }
            } catch (Exception e) {
                last = e;
            }
        }
        if (items.isEmpty() && !single) {
            return feedOne(service, kiosks.getDefaultKioskId());
        }
        if (items.isEmpty()) {
            if (last != null) {
                throw last;
            }
            throw new java.io.IOException("no feed");
        }
        return new Session(items, next, service.getServiceId(), "feed", null, url, null);
    }

    private static Session feedOne(StreamingService service, String id) throws Exception {
        KioskExtractor extractor = service.getKioskList().getExtractorById(id, null);
        extractor.fetchPage();
        ListExtractor.InfoItemsPage<?> page = extractor.getInitialPage();
        return new Session(fromItems(page.getItems()), page.getNextPage(), service.getServiceId(),
                "feed", null, extractor.getUrl(), null);
    }

    public static Session search(StreamingService service, String query) throws Exception {
        SearchQueryHandler handler = service.getSearchQHFactory().fromQuery(query);
        SearchInfo info = SearchInfo.getInfo(service, handler);
        Page next = info.hasNextPage() ? info.getNextPage() : null;
        return new Session(fromItems(info.getRelatedItems()), next, service.getServiceId(),
                "search", handler, null, null);
    }

    public static Session channel(StreamingService service, String url) throws Exception {
        ChannelInfo info = ChannelInfo.getInfo(service, url);
        List<Found> out = new ArrayList<>();
        ListLinkHandler pageTab = null;
        Page next = null;
        List<ListLinkHandler> tabs = info.getTabs();
        if (tabs != null) {
            for (ListLinkHandler tab : tabs) {
                List<String> filters = tab.getContentFilters();
                if (filters == null || !wanted(filters)) {
                    continue;
                }
                try {
                    ChannelTabInfo page = ChannelTabInfo.getInfo(service, tab);
                    out.addAll(fromItems(page.getRelatedItems()));
                    if (pageTab == null && page.hasNextPage()) {
                        pageTab = tab;
                        next = page.getNextPage();
                    }
                } catch (Exception ignored) {
                    // One tab failing leaves the others.
                }
            }
        }
        return new Session(out, next, service.getServiceId(), "channel", null, url, pageTab);
    }

    public static Session playlist(StreamingService service, String url) throws Exception {
        PlaylistInfo info = PlaylistInfo.getInfo(service, url);
        Page next = info.hasNextPage() ? info.getNextPage() : null;
        return new Session(fromItems(info.getRelatedItems()), next, service.getServiceId(),
                "playlist", null, url, null);
    }

    public static Session more(Session session) throws Exception {
        if (session == null || session.next == null) {
            return session;
        }
        StreamingService service = NewPipe.getService(session.serviceId);
        ListExtractor.InfoItemsPage<? extends InfoItem> page;
        if ("search".equals(session.mode)) {
            page = SearchInfo.getMoreItems(service, session.search, session.next);
        } else if ("playlist".equals(session.mode)) {
            page = PlaylistInfo.getMoreItems(service, session.listUrl, session.next);
        } else if ("feed".equals(session.mode)) {
            page = KioskInfo.getMoreItems(service, session.listUrl, session.next);
        } else if (session.tab != null) {
            page = ChannelTabInfo.getMoreItems(service, session.tab, session.next);
        } else {
            return session;
        }
        List<Found> combined = new ArrayList<>(session.items);
        combined.addAll(fromItems(page.getItems()));
        Page next = page.hasNextPage() ? page.getNextPage() : null;
        return new Session(combined, next, session.serviceId, session.mode,
                session.search, session.listUrl, session.tab);
    }

    private static boolean wanted(List<String> filters) {
        return filters.contains(ChannelTabs.VIDEOS)
                || filters.contains(ChannelTabs.PLAYLISTS)
                || filters.contains(ChannelTabs.TRACKS)
                || filters.contains(ChannelTabs.SHORTS)
                || filters.contains(ChannelTabs.ALBUMS);
    }

    static List<Found> fromItems(List<? extends InfoItem> items) {
        List<Found> out = new ArrayList<>();
        if (items == null) {
            return out;
        }
        for (InfoItem item : items) {
            if (item == null || item.getUrl() == null || item.getUrl().isEmpty()) {
                continue;
            }
            String kind = "video";
            String meta = "";
            if (item.getInfoType() == InfoItem.InfoType.CHANNEL) {
                kind = "channel";
            } else if (item.getInfoType() == InfoItem.InfoType.PLAYLIST) {
                kind = "playlist";
                if (item instanceof PlaylistInfoItem playlist) {
                    meta = playlist.getUploaderName() == null ? "" : playlist.getUploaderName();
                }
            } else if (item.getInfoType() == InfoItem.InfoType.COMMENT) {
                continue;
            } else if (item instanceof StreamInfoItem stream) {
                meta = stream.getUploaderName() == null ? "" : stream.getUploaderName();
                if (stream.getDuration() > 0) {
                    meta = meta + " · " + clock(stream.getDuration());
                }
            }
            out.add(new Found(kind, item.getName() == null ? item.getUrl() : item.getName(),
                    item.getUrl(), meta, thumbnailOf(item)));
        }
        return out;
    }

    public static String thumbnailOf(InfoItem item) {
        return item == null ? "" : thumbnailOf(item.getThumbnails());
    }

    public static String thumbnailOf(java.util.List<Image> images) {
        if (images == null || images.isEmpty()) {
            return "";
        }
        for (int i = images.size() - 1; i >= 0; i--) {
            String url = images.get(i).getUrl();
            if (url != null && !url.isEmpty()) {
                return url;
            }
        }
        return "";
    }

    private static String clock(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) {
            return h + ":" + two(m) + ":" + two(s);
        }
        return m + ":" + two(s);
    }

    private static String two(long n) {
        return n < 10 ? "0" + n : Long.toString(n);
    }
}
