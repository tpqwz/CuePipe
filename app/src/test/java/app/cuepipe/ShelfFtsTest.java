package app.cuepipe;

import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ShelfFtsTest {
    @Test
    public void twoTracksSearchAndReplace() throws Exception {
        Connection c = DriverManager.getConnection("jdbc:sqlite::memory:");
        JdbcSql db = new JdbcSql(c);
        for (String sql : Shelf.SCHEMA) {
            db.exec(sql);
        }
        Cues.Transcript en = Cues.parse(
                "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nalpha beta\n\n"
                        + "00:00:05.000 --> 00:00:06.000\ngamma delta\n",
                "vtt", false);
        Cues.Transcript ja = Cues.parse(
                "WEBVTT\n\n00:00:01.200 --> 00:00:02.200\nアルファ\n",
                "vtt", true);
        List<Shelf.TrackIn> tracks = List.of(
                new Shelf.TrackIn("en", en.source, "en · manual", en),
                new Shelf.TrackIn("ja", ja.source, "ja · auto", ja));
        long id = Shelf.save(db, "SoundCloud", "UCtest", "Example",
                "https://example.test/channel", "vid",
                "A talk", "https://example.test/watch?v=vid",
                "https://example.test/play.m3u8", tracks);
        List<Shelf.Track> stored = Shelf.readTracks(db, id);
        assertEquals(2, stored.size());
        int offset = Shelf.searchByte(db, stored.get(0).id, "gamma");
        List<Cues.Span> spans = Shelf.readSpans(db, stored.get(0).id);
        assertEquals(1, Cues.spanIndex(spans, offset));
        assertEquals(5000, spans.get(1).t0);
        assertEquals("アルファ", Shelf.readSpans(db, stored.get(1).id).get(0).text);

        Cues.Transcript replaced = Cues.parse(
                "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nonly alpha\n", "vtt", false);
        long again = Shelf.save(db, "SoundCloud", "UCtest", "Example",
                "https://example.test/channel", "vid",
                "A talk", "https://example.test/watch?v=vid",
                "https://example.test/play.m3u8",
                List.of(new Shelf.TrackIn("en", "manual", "en · manual", replaced)));
        assertEquals(id, again);
        Shelf.Track only = Shelf.readTracks(db, id).get(0);
        assertEquals(-1, Shelf.searchByte(db, only.id, "gamma"));
        assertEquals(0, Cues.spanIndex(Shelf.readSpans(db, only.id),
                Shelf.searchByte(db, only.id, "alpha")));
        Shelf.Video latest = Shelf.latest(db);
        assertEquals("Example", latest.channel);
        assertEquals("SoundCloud", latest.service);
        assertTrue(latest.playUrl.endsWith(".m3u8"));

        Cues.Transcript other = Cues.parse(
                "WEBVTT\n\n00:00:02.000 --> 00:00:03.000\nzebra stripe\n", "vtt", false);
        Shelf.save(db, "YouTube", "UCother", "Other", "https://example.test/other", "other",
                "Zebra hour", "https://example.test/z", "https://example.test/z.m3u8",
                List.of(new Shelf.TrackIn("en", "manual", "en · manual", other)));
        List<Shelf.Video> byTitle = Shelf.list(db, Shelf.SORT_TITLE, null);
        assertEquals("A talk", byTitle.get(0).title);
        assertEquals("Zebra hour", byTitle.get(1).title);
        assertEquals(1, Shelf.list(db, Shelf.SORT_NEWEST, "YouTube").size());
        List<Shelf.Hit> hits = Shelf.searchAll(db, db, "zebra");
        assertEquals(1, hits.size());
        assertEquals("zebra stripe", hits.get(0).quote);
        assertEquals("Zebra hour", hits.get(0).title);
        Shelf.bookmark(db, "playlist", "YouTube", "https://example.test/list", "Mix");
        assertTrue(Shelf.bookmarked(db, "https://example.test/list"));
        assertEquals("playlist", Shelf.bookmarkRows(db).get(0)[0]);
        Shelf.forget(db, "https://example.test/list");
        assertEquals(0, Shelf.bookmarkRows(db).size());
        java.io.File kept = java.io.File.createTempFile("cue", ".m4a");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(kept)) {
            out.write('x');
        }
        assertEquals(kept.getAbsolutePath(), Shelf.prefer(kept.getAbsolutePath(), "https://x"));
        assertEquals("https://x", Shelf.prefer(kept.getAbsolutePath() + ".missing", "https://x"));
        kept.delete();
        Cues.Transcript running = Cues.parse(
                "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nrunning\n", "vtt", false);
        long runId = Shelf.save(db, "YouTube", "UCrun", "Runner",
                "https://example.test/runner", "run",
                "Running", "https://example.test/run", "https://example.test/run.m3u8",
                List.of(new Shelf.TrackIn("en", "manual", "en", running)));
        long runTrack = Shelf.readTracks(db, runId).get(0).id;
        assertTrue(Shelf.searchByte(db, runTrack, "run") >= 0);
        assertEquals("https://example.test/runner", Shelf.channelUrl(db, runId));
        Shelf.editSpan(db, db, runTrack, 0, "jogging");
        List<Cues.Span> edited = Shelf.readSpans(db, runTrack);
        assertEquals("jogging", edited.get(0).text);
        assertEquals(1000, edited.get(0).t0);
        assertEquals("edited", Shelf.readTracks(db, runId).get(0).source);
        assertTrue(Shelf.searchByte(db, runTrack, "jogging") >= 0);
        assertEquals(-1, Shelf.searchByte(db, runTrack, "running"));
        Cues.Transcript fresh = Cues.parse(
                "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nfresh words\n", "vtt", false);
        Cues.Transcript japanese = Cues.parse(
                "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\n新しい\n", "vtt", true);
        Shelf.save(db, "YouTube", "UCrun", "Runner",
                "https://example.test/runner", "run",
                "Running", "https://example.test/run", "https://example.test/run.m3u8",
                List.of(
                        new Shelf.TrackIn("en", "manual", "en · manual", fresh),
                        new Shelf.TrackIn("ja", "auto", "ja · auto", japanese)));
        List<Shelf.Track> after = Shelf.readTracks(db, runId);
        assertEquals(2, after.size());
        boolean sawEdit = false;
        boolean sawJapanese = false;
        for (Shelf.Track track : after) {
            if ("edited".equals(track.source)) {
                sawEdit = true;
                assertEquals("jogging", Shelf.readSpans(db, track.id).get(0).text);
            }
            if ("ja".equals(track.lang)) {
                sawJapanese = true;
            }
            assertEquals(false, "fresh words".equals(
                    Shelf.readSpans(db, track.id).isEmpty()
                            ? "" : Shelf.readSpans(db, track.id).get(0).text));
        }
        assertTrue(sawEdit);
        assertTrue(sawJapanese);
        c.close();
    }

    @Test
    public void followKeepsAChannel() throws Exception {
        Connection c = DriverManager.getConnection("jdbc:sqlite::memory:");
        JdbcSql db = new JdbcSql(c);
        for (String sql : Shelf.SCHEMA) {
            db.exec(sql);
        }
        Shelf.bookmark(db, "channel", "YouTube", "https://example.test/c", "Example");
        assertEquals(false, Shelf.followed(db, "https://example.test/c"));
        Shelf.follow(db, "YouTube", "https://example.test/c", "Example", true);
        assertEquals(true, Shelf.followed(db, "https://example.test/c"));
        assertEquals(1, Shelf.follows(db).size());
        assertEquals(1, Shelf.checkRows(db).size());
        Shelf.follow(db, "YouTube", "https://example.test/c", "Example", false);
        assertEquals(false, Shelf.followed(db, "https://example.test/c"));
        assertEquals(0, Shelf.checkRows(db).size());
        c.close();
    }

    /** JDBC stand-in for the Android SQLite binding. */
    private static final class JdbcSql implements Shelf.Sql, Shelf.SqlRows {
        private final Connection c;

        JdbcSql(Connection c) {
            this.c = c;
        }

        @Override
        public void exec(String sql, Object... args) {
            try (PreparedStatement st = c.prepareStatement(sql)) {
                bind(st, args);
                st.execute();
            } catch (SQLException e) {
                throw new RuntimeException(sql, e);
            }
        }

        @Override
        public String text(String sql, Object... args) {
            List<String[]> rows = rows(sql, args);
            if (rows.isEmpty() || rows.get(0).length == 0) {
                return null;
            }
            return rows.get(0)[0];
        }

        @Override
        public long num(String sql, Object... args) {
            String value = text(sql, args);
            if (value == null) {
                return 0;
            }
            return Long.parseLong(value);
        }

        @Override
        public List<String[]> rows(String sql, Object... args) {
            try (PreparedStatement st = c.prepareStatement(sql)) {
                bind(st, args);
                try (ResultSet rs = st.executeQuery()) {
                    int n = rs.getMetaData().getColumnCount();
                    List<String[]> out = new ArrayList<>();
                    while (rs.next()) {
                        String[] row = new String[n];
                        for (int i = 0; i < n; i++) {
                            row[i] = rs.getString(i + 1);
                        }
                        out.add(row);
                    }
                    return out;
                }
            } catch (SQLException e) {
                throw new RuntimeException(sql, e);
            }
        }

        private static void bind(PreparedStatement st, Object[] args) throws SQLException {
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg == null) {
                    st.setNull(i + 1, java.sql.Types.VARCHAR);
                } else if (arg instanceof Long) {
                    st.setLong(i + 1, (Long) arg);
                } else if (arg instanceof Integer) {
                    st.setLong(i + 1, (Integer) arg);
                } else {
                    st.setString(i + 1, arg.toString());
                }
            }
        }
    }
}
