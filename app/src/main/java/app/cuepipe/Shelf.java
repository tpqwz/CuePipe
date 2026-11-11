package app.cuepipe;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import java.util.ArrayList;
import java.util.List;

/**
 * One shelf. The transcript body lives in docs. fts is FTS4 external content
 * over that body, so offsets() are bytes into the same text the spans store.
 */
public final class Shelf {
    public interface Sql {
        void exec(String sql, Object... args);

        /** First column of the first row, or null when there is no row. */
        String text(String sql, Object... args);

        /** First column of the first row as a long, or 0 when there is no row. */
        long num(String sql, Object... args);
    }

    public static final class Video {
        public final long id;
        public final String title;
        public final String channel;
        public final String playUrl;
        public final String pageUrl;
        public final String service;
        public final String thumb;

        public Video(long id, String title, String channel, String playUrl, String pageUrl,
                     String service) {
            this(id, title, channel, playUrl, pageUrl, service, "");
        }

        public Video(long id, String title, String channel, String playUrl, String pageUrl,
                     String service, String thumb) {
            this.id = id;
            this.title = title;
            this.channel = channel;
            this.playUrl = playUrl;
            this.pageUrl = pageUrl;
            this.service = service;
            this.thumb = thumb == null ? "" : thumb;
        }
    }

    /** One caption track to store. */
    public static final class TrackIn {
        public final String lang;
        public final String source;
        public final String label;
        public final Cues.Transcript transcript;

        public TrackIn(String lang, String source, String label, Cues.Transcript transcript) {
            this.lang = lang;
            this.source = source;
            this.label = label;
            this.transcript = transcript;
        }
    }

    /** A caption track already on the shelf. */
    public static final class Track {
        public final long id;
        public final String lang;
        public final String source;
        public final String label;

        public Track(long id, String lang, String source, String label) {
            this.id = id;
            this.lang = lang;
            this.source = source;
            this.label = label;
        }
    }

    public static final String BOOKMARK_SQL =
            "CREATE TABLE IF NOT EXISTS bookmark ("
                    + "id INTEGER PRIMARY KEY,"
                    + "kind TEXT NOT NULL,"
                    + "service TEXT NOT NULL,"
                    + "url TEXT NOT NULL UNIQUE,"
                    + "name TEXT NOT NULL,"
                    + "follow INTEGER NOT NULL DEFAULT 0)";

    public static final String[] SCHEMA = {
            "CREATE TABLE channel ("
                    + "id INTEGER PRIMARY KEY,"
                    + "service TEXT NOT NULL,"
                    + "key TEXT NOT NULL,"
                    + "name TEXT NOT NULL,"
                    + "url TEXT NOT NULL DEFAULT '',"
                    + "UNIQUE(service, key))",
            "CREATE TABLE video ("
                    + "id INTEGER PRIMARY KEY,"
                    + "channel_id INTEGER NOT NULL,"
                    + "service TEXT NOT NULL,"
                    + "key TEXT NOT NULL,"
                    + "title TEXT NOT NULL,"
                    + "url TEXT NOT NULL,"
                    + "play_url TEXT NOT NULL,"
                    + "local_path TEXT NOT NULL DEFAULT '',"
                    + "thumb TEXT NOT NULL DEFAULT '',"
                    + "UNIQUE(service, key))",
            "CREATE TABLE track ("
                    + "id INTEGER PRIMARY KEY,"
                    + "video_id INTEGER NOT NULL,"
                    + "lang TEXT NOT NULL,"
                    + "source TEXT NOT NULL,"
                    + "label TEXT NOT NULL)",
            "CREATE TABLE span ("
                    + "track_id INTEGER NOT NULL,"
                    + "idx INTEGER NOT NULL,"
                    + "t0_ms INTEGER NOT NULL,"
                    + "t1_ms INTEGER NOT NULL,"
                    + "byte_start INTEGER NOT NULL,"
                    + "byte_len INTEGER NOT NULL,"
                    + "text TEXT NOT NULL)",
            "CREATE TABLE docs (docid INTEGER PRIMARY KEY, body TEXT NOT NULL)",
            "CREATE VIRTUAL TABLE fts USING fts4(content='docs', body, tokenize=porter)",
            "CREATE TABLE vec ("
                    + "track_id INTEGER NOT NULL,"
                    + "idx INTEGER NOT NULL,"
                    + "hash INTEGER NOT NULL,"
                    + "dim INTEGER NOT NULL,"
                    + "v BLOB NOT NULL,"
                    + "PRIMARY KEY (track_id, idx))",
            BOOKMARK_SQL,
    };

    private final Db helper;

    public Shelf(Context context) {
        helper = new Db(context);
    }

    public void close() {
        helper.close();
    }

    public AndroidSql sql() {
        return new AndroidSql(helper.getWritableDatabase());
    }

    /**
     * Stores the caption tracks of one video. A video already on the shelf drops its
     * unedited caption tracks and keeps an edited track, plus speech, burned-in, and
     * translation tracks. An incoming caption in a language that was edited is skipped.
     * shortcut: the FTS4 delete command errors on this SQLite, so a replace rebuilds the index.
     */
    public static long save(Sql db, String service, String channelKey, String channelName,
                            String channelUrl, String videoKey, String title, String pageUrl,
                            String playUrl, List<TrackIn> tracks) {
        // API 26 ships SQLite 3.18, which has no UPSERT. Look up, then write.
        String haveChannel = db.text(
                "SELECT id FROM channel WHERE service=? AND key=?", service, channelKey);
        long channelId;
        if (haveChannel == null) {
            db.exec("INSERT INTO channel(service, key, name, url) VALUES(?,?,?,?)",
                    service, channelKey, channelName, channelUrl == null ? "" : channelUrl);
            channelId = db.num("SELECT last_insert_rowid()");
        } else {
            channelId = Long.parseLong(haveChannel);
            db.exec("UPDATE channel SET name=?, url=? WHERE id=?",
                    channelName, channelUrl == null ? "" : channelUrl, channelId);
        }

        String existing = db.text(
                "SELECT id FROM video WHERE service=? AND key=?", service, videoKey);
        long videoId;
        if (existing == null) {
            db.exec("INSERT INTO video(channel_id, service, key, title, url, play_url) "
                            + "VALUES(?,?,?,?,?,?)",
                    channelId, service, videoKey, title, pageUrl, playUrl);
            videoId = db.num("SELECT last_insert_rowid()");
        } else {
            videoId = Long.parseLong(existing);
            db.exec("DELETE FROM span WHERE track_id IN (SELECT id FROM track WHERE video_id=? "
                            + "AND source IN ('manual','auto'))",
                    videoId);
            db.exec("DELETE FROM docs WHERE docid IN (SELECT id FROM track WHERE video_id=? "
                            + "AND source IN ('manual','auto'))",
                    videoId);
            db.exec("DELETE FROM vec WHERE track_id IN (SELECT id FROM track WHERE video_id=? "
                            + "AND source IN ('manual','auto'))",
                    videoId);
            db.exec("DELETE FROM track WHERE video_id=? AND source IN ('manual','auto')", videoId);
            db.exec("UPDATE video SET channel_id=?, title=?, url=?, play_url=? WHERE id=?",
                    channelId, title, pageUrl, playUrl, videoId);
        }

        for (TrackIn track : tracks) {
            if (sameLanguageEdited(db, videoId, track)) {
                continue;
            }
            db.exec("INSERT INTO track(video_id, lang, source, label) VALUES(?,?,?,?)",
                    videoId, track.lang, track.source, track.label);
            long trackId = db.num("SELECT last_insert_rowid()");
            db.exec("INSERT INTO docs(docid, body) VALUES(?,?)", trackId, track.transcript.body);
            for (int i = 0; i < track.transcript.spans.size(); i++) {
                Cues.Span s = track.transcript.spans.get(i);
                db.exec("INSERT INTO span(track_id, idx, t0_ms, t1_ms, byte_start, byte_len, text) "
                                + "VALUES(?,?,?,?,?,?,?)",
                        trackId, i, s.t0, s.t1, s.byteStart, s.byteLen, s.text);
            }
        }
        db.exec("INSERT INTO fts(fts) VALUES('rebuild')");
        return videoId;
    }

    /** Appends one track to a video already on the shelf and rebuilds the index. */
    public static void addTrack(Sql db, long videoId, TrackIn track) {
        db.exec("INSERT INTO track(video_id, lang, source, label) VALUES(?,?,?,?)",
                videoId, track.lang, track.source, track.label);
        long trackId = db.num("SELECT last_insert_rowid()");
        db.exec("INSERT INTO docs(docid, body) VALUES(?,?)", trackId, track.transcript.body);
        for (int i = 0; i < track.transcript.spans.size(); i++) {
            Cues.Span span = track.transcript.spans.get(i);
            db.exec("INSERT INTO span(track_id, idx, t0_ms, t1_ms, byte_start, byte_len, text) "
                            + "VALUES(?,?,?,?,?,?,?)",
                    trackId, i, span.t0, span.t1, span.byteStart, span.byteLen, span.text);
        }
        db.exec("INSERT INTO fts(fts) VALUES('rebuild')");
    }

    /** Replaces the words of one span. Times stay. The track is marked edited. */
    public static void editSpan(Sql db, SqlRows rows, long trackId, int index, String text) {
        List<Cues.Span> spans = readSpans(rows, trackId);
        if (index < 0 || index >= spans.size()) {
            return;
        }
        String cleaned = text == null ? "" : text.trim();
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < spans.size(); i++) {
            Cues.Span span = spans.get(i);
            String line = i == index ? cleaned : span.text;
            if (body.length() > 0) {
                body.append('\n');
            }
            int start = body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            body.append(line);
            int end = body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            db.exec("UPDATE span SET text=?, byte_start=?, byte_len=? WHERE track_id=? AND idx=?",
                    line, start, end - start, trackId, i);
        }
        db.exec("UPDATE docs SET body=? WHERE docid=?", body.toString(), trackId);
        db.exec("UPDATE track SET source='edited' WHERE id=?", trackId);
        db.exec("INSERT INTO fts(fts) VALUES('rebuild')");
    }

    private static boolean sameLanguageEdited(Sql db, long videoId, TrackIn track) {
        if (track.lang == null || track.lang.isEmpty()) {
            return false;
        }
        if (!"manual".equals(track.source) && !"auto".equals(track.source)) {
            return false;
        }
        String kept = db.text(
                "SELECT id FROM track WHERE video_id=? AND source='edited' AND lower(lang)=lower(?)",
                videoId, track.lang);
        return kept != null;
    }

    public static String channelUrl(Sql db, long videoId) {
        String url = db.text(
                "SELECT channel.url FROM channel JOIN video ON video.channel_id = channel.id "
                        + "WHERE video.id=?",
                videoId);
        return url == null ? "" : url;
    }

    public static Video latest(Sql db) {
        String id = db.text(
                "SELECT video.id FROM video ORDER BY video.id DESC LIMIT 1");
        if (id == null) {
            return null;
        }
        return video(db, Long.parseLong(id));
    }

    public static Video video(Sql db, long id) {
        String title = db.text("SELECT title FROM video WHERE id=?", id);
        if (title == null) {
            return null;
        }
        String channel = db.text(
                "SELECT channel.name FROM channel JOIN video ON video.channel_id=channel.id "
                        + "WHERE video.id=?",
                id);
        return new Video(
                id,
                title,
                channel == null ? "" : channel,
                db.text("SELECT play_url FROM video WHERE id=?", id),
                db.text("SELECT url FROM video WHERE id=?", id),
                db.text("SELECT service FROM video WHERE id=?", id),
                db.text("SELECT thumb FROM video WHERE id=?", id));
    }

    public static void setThumb(Sql db, long videoId, String thumb) {
        db.exec("UPDATE video SET thumb=? WHERE id=?", thumb == null ? "" : thumb, videoId);
    }

    public static final String TRACKS_SQL =
            "SELECT id, lang, source, label FROM track WHERE video_id=? ORDER BY id";

    public static List<Track> readTracks(SqlRows rows, long videoId) {
        List<Track> out = new ArrayList<>();
        for (String[] row : rows.rows(TRACKS_SQL, videoId)) {
            out.add(new Track(Long.parseLong(row[0]), row[1], row[2], row[3]));
        }
        return out;
    }

    /** Rows of t0, t1, byteStart, byteLen, text. */
    public static List<Cues.Span> spansFrom(List<String[]> rows) {
        List<Cues.Span> out = new ArrayList<>();
        for (String[] row : rows) {
            out.add(new Cues.Span(
                    Integer.parseInt(row[0]),
                    Integer.parseInt(row[1]),
                    Integer.parseInt(row[2]),
                    Integer.parseInt(row[3]),
                    row[4]));
        }
        return out;
    }

    public static final String SPANS_SQL =
            "SELECT t0_ms, t1_ms, byte_start, byte_len, text FROM span "
                    + "WHERE track_id=? ORDER BY idx";

    public static final String OFFSETS_SQL =
            "SELECT offsets(fts) FROM fts WHERE docid=? AND fts MATCH ?";

    public static final int SORT_NEWEST = 0;
    public static final int SORT_TITLE = 1;
    public static final int SORT_CHANNEL = 2;

    /** A video on the shelf, or a search hit inside one of its tracks. */
    public static final class Hit {
        public final long videoId;
        public final long trackId;
        public final int byteOffset;
        public final String title;
        public final String channel;
        public final String quote;

        public Hit(long videoId, long trackId, int byteOffset, String title, String channel,
                   String quote) {
            this.videoId = videoId;
            this.trackId = trackId;
            this.byteOffset = byteOffset;
            this.title = title;
            this.channel = channel;
            this.quote = quote;
        }
    }

    public static List<Video> list(SqlRows rows, int sort, String service) {
        boolean filter = service != null && !service.isEmpty();
        String order;
        if (sort == SORT_TITLE) {
            order = "video.title COLLATE NOCASE, video.id DESC";
        } else if (sort == SORT_CHANNEL) {
            order = "channel.name COLLATE NOCASE, video.title COLLATE NOCASE";
        } else {
            order = "video.id DESC";
        }
        String sql = "SELECT video.id, video.title, channel.name, video.service, "
                + "video.play_url, video.url, video.thumb FROM video "
                + "JOIN channel ON channel.id = video.channel_id"
                + (filter ? " WHERE video.service=?" : "")
                + " ORDER BY " + order;
        List<String[]> raw = filter ? rows.rows(sql, service) : rows.rows(sql);
        List<Video> out = new ArrayList<>();
        for (String[] row : raw) {
            out.add(new Video(
                    Long.parseLong(row[0]), row[1], row[2], row[4], row[5], row[3], row[6]));
        }
        return out;
    }

    public static List<Video> kept(SqlRows rows) {
        String sql = "SELECT video.id, video.title, channel.name, video.service, "
                + "video.play_url, video.url, video.thumb FROM video "
                + "JOIN channel ON channel.id = video.channel_id "
                + "WHERE video.local_path != '' ORDER BY video.id DESC";
        List<Video> out = new ArrayList<>();
        for (String[] row : rows.rows(sql)) {
            out.add(new Video(
                    Long.parseLong(row[0]), row[1], row[2], row[4], row[5], row[3], row[6]));
        }
        return out;
    }

    public static List<String> services(SqlRows rows) {
        List<String> out = new ArrayList<>();
        for (String[] row : rows.rows(
                "SELECT DISTINCT service FROM video ORDER BY service COLLATE NOCASE")) {
            if (row[0] != null && !row[0].isEmpty()) {
                out.add(row[0]);
            }
        }
        return out;
    }

    /** First hit in each matching track, across the whole shelf. */
    public static List<Hit> searchAll(Sql db, SqlRows rows, String match) {
        List<Hit> hits = new ArrayList<>();
        for (String[] found : rows.rows(
                "SELECT docid, offsets(fts) FROM fts WHERE fts MATCH ?", match)) {
            long trackId = Long.parseLong(found[0]);
            int offset = Cues.firstOffset(found[1]);
            if (offset < 0) {
                continue;
            }
            String videoId = db.text(
                    "SELECT video.id FROM track JOIN video ON video.id = track.video_id "
                            + "WHERE track.id=?",
                    trackId);
            if (videoId == null) {
                continue;
            }
            Video video = video(db, Long.parseLong(videoId));
            List<Cues.Span> spans = readSpans(rows, trackId);
            int at = Cues.spanIndex(spans, offset);
            String quote = at < 0 ? "" : spans.get(at).text;
            hits.add(new Hit(video.id, trackId, offset, video.title, video.channel, quote));
        }
        return hits;
    }

    public static void bookmark(Sql db, String kind, String service, String url, String name) {
        String have = db.text("SELECT id FROM bookmark WHERE url=?", url);
        if (have == null) {
            db.exec("INSERT INTO bookmark(kind, service, url, name) VALUES(?,?,?,?)",
                    kind, service, url, name);
        } else {
            db.exec("UPDATE bookmark SET kind=?, service=?, name=? WHERE url=?",
                    kind, service, name, url);
        }
    }

    public static void forget(Sql db, String url) {
        db.exec("DELETE FROM bookmark WHERE url=?", url);
    }

    public static boolean bookmarked(Sql db, String url) {
        return db.text("SELECT id FROM bookmark WHERE url=?", url) != null;
    }

    /** A followed channel. Check fetches its new videos. */
    public static void follow(Sql db, String service, String url, String name, boolean on) {
        if (url == null || url.isEmpty()) {
            return;
        }
        bookmark(db, "channel", service, url, name);
        db.exec("UPDATE bookmark SET follow=? WHERE url=?", on ? 1 : 0, url);
    }

    public static boolean followed(Sql db, String url) {
        return "1".equals(db.text("SELECT follow FROM bookmark WHERE url=?", url));
    }

    public static List<String[]> follows(SqlRows rows) {
        return rows.rows(
                "SELECT kind, service, url, name FROM bookmark WHERE follow=1 ORDER BY name");
    }

    /** Playlists you saved, and channels you follow. */
    public static List<String[]> checkRows(SqlRows rows) {
        return rows.rows(
                "SELECT kind, service, url, name FROM bookmark "
                        + "WHERE kind='playlist' OR follow=1 ORDER BY id DESC");
    }

    public static boolean known(Sql db, String url) {
        return db.text("SELECT id FROM video WHERE url=?", url) != null;
    }

    public static String localPath(Sql db, long videoId) {
        String path = db.text("SELECT local_path FROM video WHERE id=?", videoId);
        return path == null ? "" : path;
    }

    public static void setLocalPath(Sql db, long videoId, String path) {
        db.exec("UPDATE video SET local_path=? WHERE id=?", path, videoId);
    }

    public static void setPlayUrl(Sql db, long videoId, String playUrl) {
        db.exec("UPDATE video SET play_url=? WHERE id=?", playUrl, videoId);
    }

    /** A downloaded file wins. Otherwise the fresh stream URL. */
    public static String prefer(String localPath, String freshUrl) {
        if (localPath != null && !localPath.isEmpty()) {
            java.io.File file = new java.io.File(localPath);
            if (file.isFile() && file.length() > 0) {
                return localPath;
            }
        }
        return freshUrl == null ? "" : freshUrl;
    }

    public static List<String[]> bookmarkRows(SqlRows rows) {
        return rows.rows(
                "SELECT kind, service, url, name FROM bookmark ORDER BY id DESC");
    }

    public static List<Cues.Span> readSpans(SqlRows rows, long trackId) {
        return spansFrom(rows.rows(SPANS_SQL, trackId));
    }

    /** Byte offset of the first hit in this track, or -1. docid is the track id. */
    public static int searchByte(Sql db, long trackId, String match) {
        String offsets = db.text(OFFSETS_SQL, trackId, match);
        return Cues.firstOffset(offsets);
    }

    /** Multi-column reads. Sql.text is the one-column case. */
    public interface SqlRows {
        List<String[]> rows(String sql, Object... args);
    }

    private static final class Db extends SQLiteOpenHelper {
        Db(Context context) {
            super(context, "cuepipe.db", null, 7);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            for (String sql : SCHEMA) {
                execFts(db, sql);
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                db.execSQL(BOOKMARK_SQL);
            }
            if (oldVersion >= 2 && oldVersion < 7) {
                db.execSQL("ALTER TABLE bookmark ADD COLUMN follow INTEGER NOT NULL DEFAULT 0");
                db.execSQL("UPDATE bookmark SET follow=1 WHERE kind='channel'");
            }
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE video ADD COLUMN local_path TEXT NOT NULL DEFAULT ''");
                db.execSQL("DROP TABLE IF EXISTS fts");
                execFts(db, "CREATE VIRTUAL TABLE fts USING fts4(content='docs', body, tokenize=porter)");
                db.execSQL("INSERT INTO fts(fts) VALUES('rebuild')");
            }
            if (oldVersion < 4) {
                db.execSQL("ALTER TABLE channel ADD COLUMN url TEXT NOT NULL DEFAULT ''");
            }
            if (oldVersion < 5) {
                db.execSQL("ALTER TABLE video ADD COLUMN thumb TEXT NOT NULL DEFAULT ''");
            }
            if (oldVersion < 6) {
                db.execSQL("CREATE TABLE IF NOT EXISTS vec ("
                        + "track_id INTEGER NOT NULL,"
                        + "idx INTEGER NOT NULL,"
                        + "hash INTEGER NOT NULL,"
                        + "dim INTEGER NOT NULL,"
                        + "v BLOB NOT NULL,"
                        + "PRIMARY KEY (track_id, idx))");
            }
        }

        private static void execFts(SQLiteDatabase db, String sql) {
            if (!sql.contains("tokenize=porter")) {
                db.execSQL(sql);
                return;
            }
            try {
                db.execSQL(sql);
            } catch (android.database.SQLException e) {
                db.execSQL("CREATE VIRTUAL TABLE fts USING fts4(content='docs', body)");
            }
        }
    }

    public static final class AndroidSql implements Sql, SqlRows {
        private final SQLiteDatabase db;

        public AndroidSql(SQLiteDatabase db) {
            this.db = db;
        }

        @Override
        public void exec(String sql, Object... args) {
            if (args == null || args.length == 0) {
                db.execSQL(sql);
                return;
            }
            SQLiteStatement st = db.compileStatement(sql);
            try {
                bind(st, args);
                st.execute();
            } finally {
                st.close();
            }
        }

        @Override
        public String text(String sql, Object... args) {
            List<String[]> rows = rows(sql, args);
            if (rows.isEmpty()) {
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
            String[] bound = new String[args.length];
            for (int i = 0; i < args.length; i++) {
                bound[i] = args[i] == null ? null : args[i].toString();
            }
            List<String[]> out = new ArrayList<>();
            try (Cursor c = db.rawQuery(sql, bound)) {
                while (c.moveToNext()) {
                    String[] row = new String[c.getColumnCount()];
                    for (int i = 0; i < row.length; i++) {
                        row[i] = c.getString(i);
                    }
                    out.add(row);
                }
            }
            return out;
        }

        public float[] readVec(long trackId, int idx, int hash) {
            try (Cursor c = db.rawQuery(
                    "SELECT hash, dim, v FROM vec WHERE track_id=? AND idx=?",
                    new String[]{Long.toString(trackId), Integer.toString(idx)})) {
                if (!c.moveToFirst()) {
                    return null;
                }
                if (c.getInt(0) != hash) {
                    return null;
                }
                byte[] blob = c.getBlob(2);
                int dim = c.getInt(1);
                if (blob == null || dim <= 0 || blob.length < dim * 4) {
                    return null;
                }
                float[] vector = new float[dim];
                java.nio.ByteBuffer.wrap(blob).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        .asFloatBuffer().get(vector);
                return vector;
            }
        }

        public void putVec(long trackId, int idx, int hash, float[] vector) {
            byte[] blob = new byte[vector.length * 4];
            java.nio.ByteBuffer.wrap(blob).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    .asFloatBuffer().put(vector);
            db.execSQL(
                    "INSERT OR REPLACE INTO vec(track_id, idx, hash, dim, v) VALUES(?,?,?,?,?)",
                    new Object[]{trackId, idx, hash, vector.length, blob});
        }

        private static void bind(SQLiteStatement st, Object[] args) {
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg == null) {
                    st.bindNull(i + 1);
                } else if (arg instanceof Long) {
                    st.bindLong(i + 1, (Long) arg);
                } else if (arg instanceof Integer) {
                    st.bindLong(i + 1, (Integer) arg);
                } else {
                    st.bindString(i + 1, arg.toString());
                }
            }
        }
    }
}
