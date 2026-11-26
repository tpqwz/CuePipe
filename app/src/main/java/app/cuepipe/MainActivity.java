package app.cuepipe;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.ui.PlayerView;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@OptIn(markerClass = UnstableApi.class)
public final class MainActivity extends AppCompatActivity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Shelf shelf;
    private Shelf.AndroidSql db;
    private ExoPlayer player;
    private TextView title;
    private TextView status;
    private TextView burn;
    private TextView libraryStatus;
    private ListView library;
    private EditText libraryQuery;
    private Spinner sortBy;
    private Spinner filterService;
    private View panelLibrary;
    private View panelWatch;
    private View panelExplore;
    private View panelDownloads;
    private androidx.drawerlayout.widget.DrawerLayout drawer;
    private ListView downloadsView;
    private TextView downloadsStatus;
    private List<Shelf.Video> keptVideos = Collections.emptyList();
    private int section;
    private boolean videoOpen;
    private ListView exploreList;
    private TextView exploreStatus;
    private EditText exploreQuery;
    private Spinner exploreService;
    private List<Browse.Found> exploreRows = Collections.emptyList();
    private List<Browse.Found> exploreBack = Collections.emptyList();
    private Browse.Session exploreSession;
    private String pageUrl = "";
    private String biliMixin = "";
    private String biliBuvid = "";
    private List<StreamingService> exploreServices = Collections.emptyList();
    private String playUrl = "";
    private boolean libraryHits;
    private boolean relatedMode;
    private TextView description;
    private View lineSearch;
    private List<Browse.Found> related = Collections.emptyList();
    private List<Shelf.Video> shelfVideos = Collections.emptyList();
    /** Shelf rows. A line with a null video is the channel heading. */
    private List<ShelfLine> shelfLines = Collections.emptyList();
    /** When set, the shelf shows this channel only. */
    private String shelfChannel = "";
    private List<Shelf.Hit> hits = Collections.emptyList();
    private CheckBox onScreen;
    private ListView lines;
    private static final int SLOTS = 4;
    private final Spinner[] slots = new Spinner[SLOTS];
    private boolean filling;
    private long videoId;
    private List<Shelf.Track> tracks = Collections.emptyList();
    private List<Cues.Span> spans = Collections.emptyList();
    private List<List<Cues.Span>> extras = Collections.emptyList();
    private boolean followLines = true;
    private int followed = -1;
    private int markStart = -1;
    private int markEnd = -1;
    /** Last place written for the open video, so the tick does not rewrite it. */
    private int remembered = -1;
    private boolean triedFresh;
    private boolean prose;
    private boolean excerptMode;
    /** Concepts was on for the hits now listed, so a synonym can be marked. */
    private boolean hitConcepts;
    private float speed = 1f;
    /** -1 keeps the usual stream. 0 is audio only. 360 and 720 pick a picture. */
    private int qualityHeight = -1;
    /** Positive delay shows each cue later. Stored times do not change. */
    private int offsetMs;
    private boolean mini;
    private List<Prose.Block> blocks = Collections.emptyList();
    private List<Cast.Episode> episodes = Collections.emptyList();
    /** The open feed, paged by {@link Cast#PAGE}. Empty when the list is not that feed. */
    private List<Cast.Episode> feedEpisodes = Collections.emptyList();
    private int feedAt;
    private String feedLabel = "";
    /** Link cards from an RSS feed, paged like episodes. */
    private List<Browse.Found> linkRows = Collections.emptyList();
    private int linkAt;
    private String linkLabel = "";
    /** The search field lists links instead of episodes. */
    private boolean rssPanel;
    /** More shares the saved feeds instead of turning a page. */
    private boolean shareCollection;
    /** A shared note is opening, so the first service selection must not load Home. */
    private boolean sharedOpen;
    /** Enclosure URL to the feed it came from. */
    private final Map<String, String> feedOf = new HashMap<>();
    private final Runnable playTick = new Runnable() {
        @Override
        public void run() {
            paintOnScreen();
            followTranscript();
            paintLine();
            rememberPlace();
            if (videoOpen && player.isPlaying()) {
                lines.postDelayed(this, 250);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        shelf = new Shelf(this);
        db = shelf.sql();
        title = findViewById(R.id.title);
        title.setOnLongClickListener(v -> {
            shareWatching();
            return true;
        });
        status = findViewById(R.id.status);
        lines = findViewById(R.id.lines);
        library = findViewById(R.id.library);
        libraryQuery = findViewById(R.id.library_query);
        libraryStatus = findViewById(R.id.library_status);
        sortBy = findViewById(R.id.sort_by);
        filterService = findViewById(R.id.filter_service);
        panelLibrary = findViewById(R.id.panel_library);
        panelWatch = findViewById(R.id.panel_watch);
        panelExplore = findViewById(R.id.panel_explore);
        panelDownloads = findViewById(R.id.panel_downloads);
        drawer = findViewById(R.id.drawer);
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        androidx.appcompat.app.ActionBarDrawerToggle toggle =
                new androidx.appcompat.app.ActionBarDrawerToggle(
                        this, drawer, toolbar, R.string.menu_open, R.string.menu_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        toggle.getDrawerArrowDrawable().setColor(android.graphics.Color.WHITE);
        downloadsView = findViewById(R.id.downloads);
        downloadsStatus = findViewById(R.id.downloads_status);
        exploreList = findViewById(R.id.explore);
        exploreStatus = findViewById(R.id.explore_status);
        exploreQuery = findViewById(R.id.explore_query);
        exploreService = findViewById(R.id.explore_service);
        int[] slotIds = {R.id.track0, R.id.track1, R.id.track2, R.id.track3};
        for (int i = 0; i < SLOTS; i++) {
            slots[i] = findViewById(slotIds[i]);
        }
        EditText query = findViewById(R.id.query);
        Button find = findViewById(R.id.find);
        PlayerView playerView = findViewById(R.id.player);
        startService(new Intent(this, PlayService.class));
        player = PlayService.player(this);
        playerView.setPlayer(player);
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 4);
        }
        playerView.setFocusable(false);
        playerView.setFocusableInTouchMode(false);
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
            android.widget.ProgressBar line = findViewById(R.id.play_line);
            line.setVisibility(visibility == View.VISIBLE ? View.GONE : View.VISIBLE);
            paintLine();
        });
        findViewById(R.id.play_line).setVisibility(
                playerView.isControllerFullyVisible() ? View.GONE : View.VISIBLE);
        burn = findViewById(R.id.burn);
        onScreen = findViewById(R.id.on_screen);
        onScreen.setOnCheckedChangeListener((button, checked) -> {
            lines.removeCallbacks(playTick);
            playTick.run();
        });
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                lines.removeCallbacks(playTick);
                playTick.run();
                if (!isPlaying) {
                    rememberPlace();
                }
            }

            @Override
            public void onPositionDiscontinuity(
                    Player.PositionInfo oldPosition,
                    Player.PositionInfo newPosition,
                    int reason) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK
                        || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                    followLines = true;
                }
                followed = -1;
                followTranscript();
                paintOnScreen();
                paintLine();
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (!triedFresh && pageUrl != null && !pageUrl.isEmpty()) {
                    triedFresh = true;
                    int at = (int) Math.max(player.getCurrentPosition(), 0);
                    player.clearMediaItems();
                    jump(at);
                    return;
                }
                status.setText(playFailure(error));
            }
        });

        find.setOnClickListener(v -> find(query.getText().toString().trim()));
        query.setOnEditorActionListener((v, actionId, event) -> {
            find(query.getText().toString().trim());
            return true;
        });
        findViewById(R.id.clear_query).setOnClickListener(v -> query.setText(""));
        libraryQuery.setOnEditorActionListener((v, actionId, event) -> {
            reloadLibrary();
            return true;
        });
        description = findViewById(R.id.description);
        lineSearch = findViewById(R.id.line_search);
        findViewById(R.id.show_transcript).setOnClickListener(v -> {
            relatedMode = false;
            render();
        });
        findViewById(R.id.show_related).setOnClickListener(v -> {
            relatedMode = true;
            render();
        });
        lines.setOnItemClickListener((parent, view, position, id) -> {
            if (relatedMode) {
                openRelated(position);
            } else if (prose && position >= 0 && position < blocks.size()) {
                followLines = true;
                followed = -1;
                playHere(blocks.get(position).t0);
            } else if (position >= 0 && position < spans.size()) {
                followLines = true;
                followed = -1;
                playHere(spans.get(position).t0);
            }
        });
        lines.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(android.widget.AbsListView view, int scrollState) {
                if (scrollState == android.widget.AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                    followLines = false;
                }
            }

            @Override
            public void onScroll(android.widget.AbsListView view, int first, int visible, int total) {
            }
        });
        findViewById(R.id.details).setOnClickListener(v -> {
            View block = findViewById(R.id.details_block);
            block.setVisibility(block.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });
        ((CheckBox) findViewById(R.id.prose)).setOnCheckedChangeListener((button, checked) -> {
            prose = checked;
            if (!relatedMode) {
                render();
            }
        });
        lines.setOnItemLongClickListener((parent, view, position, id) -> {
            if (!relatedMode) {
                lineMenu(position);
            }
            return true;
        });
        findViewById(R.id.subtitles).setOnClickListener(v -> pickSubtitles());
        findViewById(R.id.player_menu).setOnClickListener(v -> playerMenu());
        findViewById(R.id.mini).setOnClickListener(v -> setMini(!mini));
        findViewById(R.id.channel).setOnClickListener(v -> openChannel());
        findViewById(R.id.follow).setOnClickListener(v -> toggleFollow());
        findViewById(R.id.drawer_home).setOnClickListener(v -> {
            closeDrawer();
            rssPanel = false;
            fillExploreServices();
            showTab(0);
            loadFeed();
        });
        findViewById(R.id.drawer_rss).setOnClickListener(v -> {
            closeDrawer();
            showSavedFeeds("RSS");
        });
        findViewById(R.id.drawer_podcasts).setOnClickListener(v -> {
            closeDrawer();
            showSavedFeeds("Podcast");
        });
        findViewById(R.id.drawer_library).setOnClickListener(v -> {
            closeDrawer();
            excerptMode = false;
            shelfChannel = "";
            showTab(1);
            reloadLibrary();
        });
        findViewById(R.id.drawer_excerpts).setOnClickListener(v -> {
            closeDrawer();
            showExcerpts();
        });
        findViewById(R.id.drawer_downloads).setOnClickListener(v -> {
            closeDrawer();
            showTab(2);
        });
        findViewById(R.id.drawer_marks).setOnClickListener(v -> {
            closeDrawer();
            showTab(0);
            showBookmarks();
        });
        findViewById(R.id.drawer_follows).setOnClickListener(v -> {
            closeDrawer();
            showTab(0);
            showFollows();
        });
        findViewById(R.id.drawer_check).setOnClickListener(v -> {
            closeDrawer();
            showTab(0);
            checkFeed();
        });
        downloadsView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= keptVideos.size()) {
                return;
            }
            show(keptVideos.get(position));
            showVideo();
        });
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                View focus = getCurrentFocus();
                if (drawer.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                    drawer.closeDrawer(androidx.core.view.GravityCompat.START);
                    return;
                }
                if (focus instanceof EditText) {
                    android.view.inputmethod.InputMethodManager imm =
                            (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
                    }
                    focus.clearFocus();
                    return;
                }
                if (videoOpen) {
                    showTab(section);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
        findViewById(R.id.explore_search).setOnClickListener(v -> exploreSearch());
        exploreQuery.setOnEditorActionListener((v, actionId, event) -> {
            exploreSearch();
            return true;
        });
        findViewById(R.id.explore_more).setOnClickListener(v -> more());
        findViewById(R.id.keep).setOnClickListener(v -> keep());
        findViewById(R.id.burned).setOnClickListener(v -> burned());
        findViewById(R.id.translate).setOnClickListener(v -> translate());
        findViewById(R.id.recognize).setOnClickListener(v -> recognize());
        findViewById(R.id.explore_back).setOnClickListener(v -> {
            if (!exploreBack.isEmpty()) {
                showExplore(exploreBack, "Back");
                exploreBack = Collections.emptyList();
            }
        });
        exploreList.setOnItemClickListener((parent, view, position, id) -> openExplore(position));
        exploreList.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < exploreRows.size()) {
                Browse.Found item = exploreRows.get(position);
                if ("link".equals(item.kind)) {
                    shareText(item.name, item.name + "\n" + item.url);
                    return true;
                }
                if ("episode".equals(item.kind)) {
                    shareEpisode(item);
                    return true;
                }
            }
            toggleBookmark(position);
            return true;
        });
        findViewById(R.id.library_find).setOnClickListener(v -> reloadLibrary());
        library.setOnItemClickListener((parent, view, position, id) -> openLibraryRow(position));
        AdapterView.OnItemSelectedListener redraw = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!filling) {
                    render();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        for (Spinner slot : slots) {
            slot.setOnItemSelectedListener(redraw);
        }
        AdapterView.OnItemSelectedListener refresh = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!filling) {
                    reloadLibrary();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        List<String> sorts = new ArrayList<>();
        sorts.add("Newest");
        sorts.add("Title");
        sorts.add("Channel");
        sortBy.setAdapter(spinner(sorts));
        sortBy.setOnItemSelectedListener(refresh);
        filterService.setOnItemSelectedListener(refresh);

        Shelf.Video latest = Shelf.latest(db);
        if (latest != null && player.getMediaItemCount() == 0) {
            show(latest, false);
        } else {
            fillServices();
            reloadLibrary();
        }
        sharedOpen = feedNote(getIntent());
        fillExploreServices();
        if (sharedOpen) {
            takeShared(getIntent());
        } else if (Shelf.latest(db) != null) {
            showTab(1);
        } else {
            showTab(0);
            loadFeed();
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        takeShared(intent);
    }

    private static boolean feedNote(android.content.Intent intent) {
        if (intent == null || !android.content.Intent.ACTION_SEND.equals(intent.getAction())) {
            return false;
        }
        CharSequence extra = intent.getCharSequenceExtra(android.content.Intent.EXTRA_TEXT);
        if (extra == null) {
            return false;
        }
        String note = extra.toString();
        return note.contains("<opml") || note.contains("xmlUrl=");
    }

    /** A chat or a note shared into CuePipe. An OPML note restores the feeds. */
    private void takeShared(android.content.Intent intent) {
        if (feedNote(intent)) {
            restoreFeeds(intent.getCharSequenceExtra(android.content.Intent.EXTRA_TEXT).toString());
        }
    }

    private void loadFeed() {
        if (exploreServices.isEmpty()) {
            return;
        }
        if (chuteSelected()) {
            loadChute(Chute.POPULAR, Chute.popularBody(), "BitChute");
            return;
        }
        if (biliSelected()) {
            loadBiliHome();
            return;
        }
        if (nicoSelected()) {
            loadNicoHome();
            return;
        }
        if (rumbleSelected()) {
            noteFeed("Paste a Rumble link. The feed is blocked here.");
            return;
        }
        if (kickSelected()) {
            loadKickHome();
            return;
        }
        if (odyseeSelected()) {
            loadOdysee(Odysee.PROXY + "claim_search", Odysee.popularBody(), "Odysee");
            return;
        }
        if (fosdemSelected()) {
            loadFosdem(Fosdem.HOME, "FOSDEM");
            return;
        }
        if (archiveSelected()) {
            loadArchive(Archive.homeUrl(), "Internet Archive");
            return;
        }
        if (tedSelected()) {
            loadTedHome();
            return;
        }
        if (nasaSelected()) {
            loadNasa(Nasa.homeUrl(), "NASA+");
            return;
        }
        if (redBullSelected()) {
            loadRedBull(RedBull.HOME, "Red Bull TV");
            return;
        }
        int which = Math.max(exploreService.getSelectedItemPosition(), 0);
        StreamingService service = exploreServices.get(which);
        exploreStatus.setText("Loading");
        io.execute(() -> {
            Exception last = null;
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    Browse.Session session = Browse.feed(service);
                    String page = session.listUrl == null ? "" : session.listUrl;
                    String label = page.contains("charts.youtube.com")
                            || page.contains("/gaming/trending")
                            ? "Trending" : service.getServiceInfo().getName();
                    runOnUiThread(() -> {
                        exploreBack = Collections.emptyList();
                        showSession(session, label);
                    });
                    return;
                } catch (Exception e) {
                    last = e;
                    if (!dns(e) || attempt == 2) {
                        break;
                    }
                    try {
                        Thread.sleep(700L * (attempt + 1));
                    } catch (InterruptedException interrupted) {
                        break;
                    }
                }
            }
            String message = last.getClass().getSimpleName() + ": " + last.getMessage();
            noteFeed(message);
        });
    }

    private static boolean dns(Throwable error) {
        while (error != null) {
            if (error instanceof java.net.UnknownHostException) {
                return true;
            }
            error = error.getCause();
        }
        return false;
    }

    private void showTab(int tab) {
        section = tab;
        videoOpen = false;
        if (lines != null) {
            lines.removeCallbacks(playTick);
        }
        if (tab == 2) {
            reloadDownloads();
        }
        applyChrome();
    }

    private void showVideo() {
        videoOpen = true;
        applyChrome();
    }

    private void applyChrome() {
        panelExplore.setVisibility(!videoOpen && section == 0 ? View.VISIBLE : View.GONE);
        panelLibrary.setVisibility(!videoOpen && section == 1 ? View.VISIBLE : View.GONE);
        panelDownloads.setVisibility(!videoOpen && section == 2 ? View.VISIBLE : View.GONE);
        panelWatch.setVisibility(videoOpen ? View.VISIBLE : View.GONE);
    }

    private void closeDrawer() {
        drawer.closeDrawer(androidx.core.view.GravityCompat.START);
    }

    private void reloadDownloads() {
        keptVideos = Shelf.kept(db);
        downloadsStatus.setText(keptVideos.size() + " downloads");
        downloadsView.setAdapter(new DownloadAdapter());
    }

    private void burned() {
        String kept = Shelf.prefer(Shelf.localPath(db, videoId), "");
        status.setText(kept.isEmpty()
                ? "Reading, not keeping the file" : "Reading burned-in lines");
        String page = pageUrl;
        String playing = playUrl;
        long id = videoId;
        io.execute(() -> {
            java.io.File borrowed = null;
            try {
                String path = kept;
                if (path.isEmpty()) {
                    String source = readSource(page, playing, true);
                    borrowed = scratch(id, ".burn.mp4");
                    download(source, borrowed, "Reading");
                    path = borrowed.getAbsolutePath();
                }
                Cues.Transcript transcript = Burned.read(path, fraction -> runOnUiThread(
                        () -> status.setText("Burned-in " + fraction + "%")));
                if (transcript.spans.isEmpty()) {
                    runOnUiThread(() -> status.setText("No burned-in line found"));
                    return;
                }
                Shelf.addTrack(db, id, new Shelf.TrackIn("zh", "ocr", "burned-in", transcript));
                runOnUiThread(() -> show(Shelf.video(db, id)));
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> status.setText(message));
            } finally {
                if (borrowed != null) {
                    borrowed.delete();
                }
            }
        });
    }

    private void recognize() {
        String kept = Shelf.prefer(Shelf.localPath(db, videoId), "");
        status.setText(kept.isEmpty()
                ? "Recognizing, not keeping the file" : "Recognizing");
        String page = pageUrl;
        String playing = playUrl;
        long id = videoId;
        io.execute(() -> {
            java.io.File side = null;
            java.io.File borrowed = null;
            try {
                String path = kept;
                if (path.isEmpty()) {
                    String source = readSource(page, playing, false);
                    String ext = source.contains(".mp3") ? ".mp3"
                            : source.contains(".mp4") ? ".mp4" : ".m4a";
                    borrowed = scratch(id, ".speech" + ext);
                    download(source, borrowed, "Reading");
                    path = borrowed.getAbsolutePath();
                }
                float[] samples;
                try {
                    samples = Pcm.decode16kMono(path);
                } catch (java.io.IOException missing) {
                    if (missing.getMessage() == null || !missing.getMessage().equals("no audio")) {
                        throw missing;
                    }
                    if (page == null || page.isEmpty()) {
                        throw new java.io.IOException("no audio");
                    }
                    String source = "";
                    try {
                        source = audioUrl(StreamInfo.getInfo(page));
                    } catch (Exception notNewPipe) {
                        if (Bili.bvid(page) != null) {
                            source = biliAudio(page);
                        }
                    }
                    if (source.isEmpty()) {
                        throw new java.io.IOException("no audio");
                    }
                    java.io.File dir = getExternalFilesDir("media");
                    if (dir == null) {
                        dir = getFilesDir();
                    }
                    side = new java.io.File(dir, id + ".speech.m4a");
                    download(source, side, "Audio");
                    samples = Pcm.decode16kMono(side.getAbsolutePath());
                }
                Speech.Heard heard = Speech.transcribe(MainActivity.this, samples, pct ->
                        runOnUiThread(() -> status.setText(pct < 0 ? "Recognizing" : "Model " + pct + "%")));
                if (heard.transcript.spans.isEmpty()) {
                    runOnUiThread(() -> status.setText("No speech recognized"));
                    return;
                }
                Shelf.addTrack(db, id, new Shelf.TrackIn(heard.lang, "asr", "whisper", heard.transcript));
                runOnUiThread(() -> show(Shelf.video(db, id)));
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> status.setText(message));
            } finally {
                if (side != null) {
                    side.delete();
                }
                if (borrowed != null) {
                    borrowed.delete();
                }
            }
        });
    }

    /**
     * A stream to read once. Picture prefers a file with frames. Speech prefers
     * audio. The caller deletes the download and does not set {@code local_path}.
     */
    private String readSource(String page, String playing, boolean picture) throws java.io.IOException {
        try {
            NewPipe.getServiceByUrl(page);
            StreamInfo info = StreamInfo.getInfo(page);
            if (!picture) {
                String audio = audioUrl(info);
                if (!audio.isEmpty()) {
                    return audio;
                }
            }
            int height = picture && qualityHeight == 0 ? 360 : qualityHeight;
            String file = fileUrl(info, height);
            if (file.isEmpty()) {
                throw new java.io.IOException("no stream");
            }
            return file;
        } catch (java.io.IOException e) {
            throw e;
        } catch (Exception notAService) {
            if (!picture && page != null && Bili.bvid(page) != null) {
                return biliAudio(page);
            }
            if (playing == null || playing.isEmpty() || playing.startsWith("/")
                    || playing.contains(".m3u8")) {
                throw new java.io.IOException(playing != null && playing.contains(".m3u8")
                        ? "This stream is a playlist" : "no stream");
            }
            return playing;
        }
    }

    private java.io.File scratch(long id, String suffix) {
        return new java.io.File(getCacheDir(), id + suffix);
    }

    /** Downloads the episode file when the page is not a video service. */
    private void keepDirect(String source, long id) {
        if (source == null || source.isEmpty() || source.startsWith("/")) {
            status.setText("Nothing to keep");
            return;
        }
        io.execute(() -> {
            java.io.File dir = getExternalFilesDir("media");
            if (dir == null) {
                dir = getFilesDir();
            }
            String ext = source.contains(".mp3") ? ".mp3"
                    : source.contains(".mp4") || source.contains("upgcxcode")
                    || source.contains("bilivideo") ? ".mp4" : ".m4a";
            java.io.File dest = new java.io.File(dir, id + ext);
            try {
                download(source, dest, "Saving");
                Shelf.setLocalPath(db, id, dest.getAbsolutePath());
                runOnUiThread(() -> {
                    status.setText("Kept");
                    show(Shelf.video(db, id));
                });
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> status.setText(message));
            }
        });
    }

    /** Timed captions outrank an HTML page, which outranks a plain file. */
    private static int captionRank(Cast.Caption caption) {
        String suffix = captionSuffix(caption);
        if ("vtt".equals(suffix) || "srt".equals(suffix)) {
            return 0;
        }
        if ("json".equals(suffix)) {
            return 1;
        }
        if ("html".equals(suffix)) {
            return 2;
        }
        return 3;
    }

    private static String captionSuffix(Cast.Caption caption) {
        String type = caption.type.toLowerCase(Locale.US);
        String link = caption.url.toLowerCase(Locale.US);
        if (type.contains("json") || link.contains(".json")) {
            return "json";
        }
        if (type.contains("html") || link.endsWith(".html") || link.endsWith(".htm")) {
            return "html";
        }
        if (type.contains("srt") || link.contains(".srt")) {
            return "srt";
        }
        if (type.contains("plain") || link.endsWith(".txt")) {
            return "txt";
        }
        return "vtt";
    }

    private static boolean looksLikeFeed(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("rss") || lower.contains("feed") || lower.contains("podcast")
                || lower.endsWith(".xml");
    }

    /** Shows a podcast feed. Returns false when the URL is not one. */
    private boolean openFeed(String url) {
        try {
            String body = fetchText(url);
            if (!body.contains("<rss") && !body.contains("<feed")) {
                noteFeed("This feed is not a podcast");
                return false;
            }
            Cast.Show show = Cast.parse(body);
            String label = show.title.isEmpty() ? "Podcast" : show.title;
            for (Cast.Episode episode : show.episodes) {
                feedOf.put(episode.audio, url);
            }
            Shelf.follow(db, "Podcast", url, label, true);
            runOnUiThread(() -> {
                feedEpisodes = show.episodes;
                feedAt = 0;
                feedLabel = label;
                showFeedPage();
            });
            return true;
        } catch (Exception e) {
            noteFeed(e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    /** Lists the links in a feed and remembers the feed under RSS. */
    private boolean openRss(String url) {
        try {
            String body = fetchText(url);
            if (!body.contains("<rss") && !body.contains("<feed")) {
                noteFeed("This feed is not RSS");
                return false;
            }
            List<Cast.Post> posts = Cast.posts(body);
            if (posts.isEmpty()) {
                noteFeed("No links in this feed");
                return false;
            }
            String titled = Cast.feedTitle(body);
            String shown = titled.isEmpty() ? "RSS" : titled;
            List<Browse.Found> rows = new ArrayList<>();
            for (Cast.Post post : posts) {
                rows.add(new Browse.Found("link", post.title, post.url, shown));
            }
            Shelf.follow(db, "RSS", url, shown, true);
            runOnUiThread(() -> {
                linkRows = rows;
                linkAt = 0;
                linkLabel = shown;
                showLinkPage();
            });
            return true;
        } catch (Exception e) {
            noteFeed(e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    private void showLinkPage() {
        int end = Math.min(linkAt + Cast.PAGE, linkRows.size());
        List<Browse.Found> page = linkAt >= end
                ? Collections.emptyList()
                : new ArrayList<>(linkRows.subList(linkAt, end));
        feedEpisodes = Collections.emptyList();
        exploreRows = page;
        exploreSession = null;
        episodes = Collections.emptyList();
        armMore(false);
        String more = end < linkRows.size() ? " · more" : "";
        exploreStatus.setText(linkLabel + " · " + page.size() + more);
        exploreList.setAdapter(new ExploreAdapter());
        showTab(0);
    }

    private void openEpisode(String audio) {
        Cast.Episode episode = null;
        for (Cast.Episode candidate : episodes) {
            if (audio.equals(candidate.audio)) {
                episode = candidate;
                break;
            }
        }
        if (episode == null) {
            status.setText("That episode is gone");
            return;
        }
        Cast.Episode chosen = episode;
        showVideo();
        status.setText("Opening");
        io.execute(() -> {
            try {
                List<Shelf.TrackIn> incoming = new ArrayList<>();
                List<Cast.Caption> captions = new ArrayList<>(chosen.captions);
                captions.sort((a, b) -> Integer.compare(captionRank(a), captionRank(b)));
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (Cast.Caption caption : captions) {
                    String lang = caption.lang.isEmpty() ? "und" : caption.lang;
                    if (seen.contains(lang)) {
                        continue;
                    }
                    try {
                        String raw = fetchText(caption.url);
                        Cues.Transcript transcript = Cues.parse(raw, captionSuffix(caption), false);
                        if (transcript.spans.isEmpty()) {
                            continue;
                        }
                        seen.add(lang);
                        incoming.add(new Shelf.TrackIn(lang, "manual", lang + " · podcast", transcript));
                    } catch (Exception ignored) {
                        // One missing caption file does not drop the episode.
                    }
                }
                if (!chosen.chapters.isEmpty()) {
                    try {
                        Cues.Transcript chapters = Cues.parse(fetchText(chosen.chapters), "json", false);
                        if (!chapters.spans.isEmpty()) {
                            incoming.add(new Shelf.TrackIn("und", "manual", "chapters", chapters));
                        }
                    } catch (Exception ignored) {
                        // Chapters are optional.
                    }
                }
                String show = "";
                for (Browse.Found row : exploreRows) {
                    if (audio.equals(row.url)) {
                        show = row.meta;
                        break;
                    }
                }
                String feed = feedOf.containsKey(chosen.audio) ? feedOf.get(chosen.audio) : "";
                long id = Shelf.save(
                        db, "Podcast", feed.isEmpty() ? show : feed, show, feed,
                        chosen.audio, chosen.title, chosen.page, chosen.audio, incoming);
                Shelf.Video video = Shelf.video(db, id);
                boolean bare = incoming.isEmpty();
                runOnUiThread(() -> {
                    if (videoId != id) {
                        show(video, false);
                    }
                    play(chosen.audio, 0);
                    if (bare) {
                        status.setText("No transcript. Keep, then Recognize.");
                    }
                });
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> status.setText(message));
            }
        });
    }

    private boolean chuteSelected() {
        return exploreService.getSelectedItemPosition() == exploreServices.size();
    }

    private boolean biliSelected() {
        return exploreService.getSelectedItemPosition() == exploreServices.size() + 1;
    }

    private boolean nicoSelected() {
        return exploreService.getSelectedItemPosition() == exploreServices.size() + 2;
    }

    private boolean rumbleSelected() {
        return exploreService.getSelectedItemPosition() == exploreServices.size() + 3;
    }

    private boolean kickSelected() {
        return exploreService.getSelectedItemPosition() == exploreServices.size() + 4;
    }

    private boolean odyseeSelected() {
        return exploreService.getSelectedItemPosition() == exploreServices.size() + 5;
    }

    private boolean fosdemSelected() {
        return exploreService.getSelectedItemPosition() == exploreServices.size() + 6;
    }

    private boolean archiveSelected() {
        return exploreService.getSelectedItemPosition() == exploreServices.size() + 7;
    }

    private boolean tedSelected() {
        return exploreService.getSelectedItemPosition() == exploreServices.size() + 8;
    }

    private boolean nasaSelected() {
        return exploreService.getSelectedItemPosition() == exploreServices.size() + 9;
    }

    private boolean redBullSelected() {
        return exploreService.getSelectedItemPosition() == exploreServices.size() + 10;
    }

    /** Popular, search, or a channel. The first page only. */
    private void loadChute(String url, String body, String label) {
        exploreStatus.setText("Loading");
        io.execute(() -> {
            try {
                List<Browse.Found> rows = new ArrayList<>();
                for (Chute.Clip clip : Chute.videos(postJson(url, body))) {
                    rows.add(new Browse.Found(
                            "video", clip.title, clip.page, clip.channel, clip.thumb));
                }
                runOnUiThread(() -> {
                    exploreSession = null;
                    exploreBack = Collections.emptyList();
                    showExplore(rows, label);
                });
            } catch (Exception e) {
                noteFeed(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void openChute(String page) {
        showVideo();
        exploreQuery.setText(page);
        status.setText("Opening");
        io.execute(() -> {
            try {
                String id = Chute.videoId(page);
                Chute.Clip clip = Chute.video(postJson(Chute.VIDEO, Chute.idBody(id)));
                String media = Chute.media(postJson(Chute.MEDIA, Chute.idBody(id)));
                if (media.isEmpty()) {
                    throw new java.io.IOException("no stream");
                }
                String channelKey = clip.channelId.isEmpty() ? clip.channel : clip.channelId;
                long saved = Shelf.save(
                        db, "BitChute", channelKey, blank(clip.channel, "BitChute"),
                        clip.channelPage, clip.id, blank(clip.title, page), clip.page, media,
                        List.of());
                Shelf.setThumb(db, saved, clip.thumb);
                Shelf.Video video = Shelf.video(db, saved);
                String blurb = clip.description;
                runOnUiThread(() -> {
                    if (videoId != saved) {
                        show(video, false);
                    }
                    play(media, 0);
                    if (!blurb.isEmpty()) {
                        description.setText(blurb);
                    }
                    status.setText("No transcript. Keep, then Recognize.");
                });
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> status.setText(message));
            }
        });
    }

    private static String postJson(String url, String body) throws java.io.IOException {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", OkDownloader.USER_AGENT)
                .header("Content-Type", "application/json")
                .post(okhttp3.RequestBody.create(body, okhttp3.MediaType.parse("application/json")))
                .build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new java.io.IOException("HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    private void loadRedBull(String url, String label) {
        exploreStatus.setText("Loading");
        io.execute(() -> {
            try {
                List<Browse.Found> rows = new ArrayList<>();
                for (RedBull.Clip clip : RedBull.feed(fetchRedBull(url))) {
                    rows.add(new Browse.Found("video", clip.title, clip.page, "Red Bull TV", clip.thumb));
                }
                runOnUiThread(() -> {
                    exploreSession = null;
                    exploreBack = Collections.emptyList();
                    showExplore(rows, label);
                });
            } catch (Exception e) {
                noteFeed(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void openRedBull(String page) {
        showVideo();
        exploreQuery.setText(page);
        status.setText("Opening");
        io.execute(() -> {
            try {
                String id = RedBull.id(page);
                RedBull.Clip clip = RedBull.player(fetchRedBull(RedBull.playerUrl(id)));
                if (clip.media.isEmpty()) {
                    throw new java.io.IOException("no video");
                }
                java.util.ArrayList<Shelf.TrackIn> tracks = new java.util.ArrayList<>();
                for (RedBull.Caption caption : clip.captions) {
                    try {
                        Cues.Transcript transcript = Cues.parse(fetchRedBull(caption.url), "vtt", false);
                        if (!transcript.spans.isEmpty()) {
                            tracks.add(new Shelf.TrackIn(caption.lang, "manual", "captions", transcript));
                        }
                    } catch (Exception ignored) {
                        // A missing caption file still leaves the video playable.
                    }
                }
                long saved = Shelf.save(
                        db, "Red Bull TV", "redbull", "Red Bull TV", "https://www.redbull.com/",
                        id, blank(clip.title, "Red Bull TV"), blank(clip.page, page),
                        clip.media, tracks);
                Shelf.setThumb(db, saved, clip.thumb);
                Shelf.Video video = Shelf.video(db, saved);
                String note = tracks.isEmpty()
                        ? "No transcript. Keep, then Recognize."
                        : tracks.size() + " caption track" + (tracks.size() == 1 ? "" : "s");
                runOnUiThread(() -> {
                    if (videoId != saved) {
                        show(video, false);
                    }
                    play(clip.media, 0);
                    status.setText(note);
                });
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> status.setText(message));
            }
        });
    }

    private static String fetchRedBull(String url) throws java.io.IOException {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", Nico.UA)
                .header("Accept", "application/json,application/vnd.apple.mpegurl,text/vtt,*/*")
                .header("Referer", "https://www.redbull.com/")
                .build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new java.io.IOException("HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    private void loadNasa(String url, String label) {
        exploreStatus.setText("Loading");
        io.execute(() -> {
            try {
                List<Browse.Found> rows = new ArrayList<>();
                for (Nasa.Clip clip : Nasa.videos(fetchNasa(url))) {
                    rows.add(new Browse.Found("video", clip.title, clip.page, "NASA", clip.thumb));
                }
                runOnUiThread(() -> {
                    exploreSession = null;
                    exploreBack = Collections.emptyList();
                    showExplore(rows, label);
                });
            } catch (Exception e) {
                noteFeed(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void openNasa(String page) {
        showVideo();
        exploreQuery.setText(page);
        status.setText("Opening");
        io.execute(() -> {
            try {
                Nasa.Clip clip = Nasa.one(fetchNasa(Nasa.slugUrl(Nasa.slug(page))));
                if (clip.media.isEmpty()) {
                    throw new java.io.IOException("no video");
                }
                long saved = Shelf.save(
                        db, "NASA+", "nasa", "NASA", "https://plus.nasa.gov/",
                        Nasa.slug(page), blank(clip.title, page), blank(clip.page, page),
                        clip.media, List.of());
                Shelf.setThumb(db, saved, clip.thumb);
                Shelf.Video video = Shelf.video(db, saved);
                runOnUiThread(() -> {
                    if (videoId != saved) {
                        show(video, false);
                    }
                    play(clip.media, 0);
                    status.setText("No transcript. Keep, then Recognize.");
                });
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> status.setText(message));
            }
        });
    }

    private static String fetchNasa(String url) throws java.io.IOException {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", OkDownloader.USER_AGENT)
                .header("Accept", "application/json")
                .header("Referer", "https://plus.nasa.gov/")
                .build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new java.io.IOException("HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    private void loadTedHome() {
        exploreStatus.setText("Loading");
        io.execute(() -> {
            try {
                showTed(Ted.home(postTed(Ted.GRAPHQL, Ted.homeBody())), "TED");
            } catch (Exception e) {
                noteFeed(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void loadTedSearch(String query) {
        exploreStatus.setText("Searching");
        io.execute(() -> {
            try {
                showTed(Ted.hits(postTed(Ted.SEARCH, Ted.searchBody(query))), "TED");
            } catch (Exception e) {
                noteFeed(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void showTed(List<Ted.Talk> talks, String label) {
        try {
            List<Browse.Found> rows = new ArrayList<>();
            for (Ted.Talk talk : talks) {
                rows.add(new Browse.Found("video", talk.title, talk.page, talk.speaker, talk.thumb));
            }
            runOnUiThread(() -> {
                exploreSession = null;
                exploreBack = Collections.emptyList();
                showExplore(rows, label);
            });
        } catch (Exception e) {
            noteFeed(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void openTed(String page, int position) {
        showVideo();
        exploreQuery.setText(page);
        status.setText("Opening");
        io.execute(() -> {
            try {
                String slug = Ted.slug(page);
                String raw = postTed(Ted.GRAPHQL, Ted.detailsBody(slug));
                Ted.Talk talk = Ted.details(raw);
                String media = Ted.choose(talk, qualityHeight);
                if (media.isEmpty()) {
                    String head = raw.replace('\n', ' ');
                    if (head.length() > 120) {
                        head = head.substring(0, 120);
                    }
                    throw new java.io.IOException("no video: " + head);
                }
                java.util.ArrayList<Shelf.TrackIn> tracks = new java.util.ArrayList<>();
                if (!talk.metadata.isEmpty()) {
                    for (Ted.Caption caption : Ted.captions(fetchTed(talk.metadata))) {
                        try {
                            Cues.Transcript transcript = Cues.parse(fetchTed(caption.url), "vtt", false);
                            if (!transcript.spans.isEmpty()) {
                                tracks.add(new Shelf.TrackIn(caption.lang, "manual", "captions", transcript));
                            }
                        } catch (Exception ignored) {
                            // A missing caption file still leaves the talk playable.
                        }
                    }
                }
                long saved = Shelf.save(
                        db, "TED", blank(talk.speaker, slug), blank(talk.speaker, "TED"),
                        "", slug, blank(talk.title, slug), blank(talk.page, page), media, tracks);
                Shelf.setThumb(db, saved, talk.thumb);
                Shelf.Video video = Shelf.video(db, saved);
                String note = tracks.isEmpty()
                        ? "No transcript. Keep, then Recognize."
                        : tracks.size() + " caption track" + (tracks.size() == 1 ? "" : "s");
                runOnUiThread(() -> {
                    if (videoId != saved) {
                        show(video, false);
                    }
                    play(media, position);
                    status.setText(note);
                });
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> status.setText(message));
            }
        });
    }

    private static String postTed(String url, String body) throws java.io.IOException {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", OkDownloader.USER_AGENT)
                .header("Content-Type", "application/json")
                .header("Origin", "https://www.ted.com")
                .header("Referer", "https://www.ted.com/")
                .post(okhttp3.RequestBody.create(body, okhttp3.MediaType.parse("application/json")))
                .build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new java.io.IOException("HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    private static String fetchTed(String url) throws java.io.IOException {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", OkDownloader.USER_AGENT)
                .header("Referer", "https://www.ted.com/")
                .build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new java.io.IOException("HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    private void loadArchive(String url, String label) {
        exploreStatus.setText("Loading");
        io.execute(() -> {
            try {
                List<Browse.Found> rows = new ArrayList<>();
                for (Archive.Item item : Archive.docs(fetchArchive(url))) {
                    rows.add(new Browse.Found(
                            "video", item.title, item.page, item.creator, item.thumb));
                }
                runOnUiThread(() -> {
                    exploreSession = null;
                    exploreBack = Collections.emptyList();
                    showExplore(rows, label);
                });
            } catch (Exception e) {
                noteFeed(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void openArchive(String page, int position) {
        showVideo();
        exploreQuery.setText(page);
        status.setText("Opening");
        io.execute(() -> {
            try {
                String id = Archive.identifier(page);
                String metaBody = fetchArchive(Archive.metadataUrl(id));
                Archive.Item item = Archive.item(metaBody, qualityHeight);
                if (item.media.isEmpty()) {
                    throw new java.io.IOException("no video");
                }
                java.util.ArrayList<Shelf.TrackIn> tracks = new java.util.ArrayList<>();
                for (Archive.Caption caption : item.captions) {
                    try {
                        String body = fetchArchive(caption.url);
                        String kind = caption.url.contains(".vtt") ? "vtt" : "srt";
                        Cues.Transcript transcript = Cues.parse(body, kind, false);
                        if (!transcript.spans.isEmpty()) {
                            tracks.add(new Shelf.TrackIn(caption.lang, "auto", "captions", transcript));
                        }
                    } catch (Exception ignored) {
                        // A missing caption file still leaves the item playable.
                    }
                }
                long saved = Shelf.save(
                        db, "Internet Archive", blank(item.creator, id), blank(item.creator, "Internet Archive"),
                        "", id, blank(item.title, id), item.page, item.media, tracks);
                Shelf.setThumb(db, saved, item.thumb);
                Shelf.Video video = Shelf.video(db, saved);
                String note = tracks.isEmpty()
                        ? "No transcript. Keep, then Recognize."
                        : tracks.size() + " caption track" + (tracks.size() == 1 ? "" : "s");
                runOnUiThread(() -> {
                    if (videoId != saved) {
                        show(video, false);
                    }
                    play(item.media, position);
                    status.setText(note);
                });
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> status.setText(message));
            }
        });
    }

    private static String fetchArchive(String url) throws java.io.IOException {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", OkDownloader.USER_AGENT)
                .header("Accept", "application/json,text/plain,*/*")
                .build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new java.io.IOException("HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    private void loadFosdem(String url, String label) {
        exploreStatus.setText("Loading");
        io.execute(() -> {
            try {
                List<Browse.Found> rows = new ArrayList<>();
                for (Fosdem.Talk talk : Fosdem.talks(fetchFosdem(url))) {
                    rows.add(new Browse.Found(
                            "video", talk.title, talk.page, talk.speaker, talk.thumb));
                }
                runOnUiThread(() -> {
                    exploreSession = null;
                    exploreBack = Collections.emptyList();
                    showExplore(rows, label);
                });
            } catch (Exception e) {
                noteFeed(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void openFosdem(String page) {
        showVideo();
        exploreQuery.setText(page);
        status.setText("Opening");
        io.execute(() -> {
            try {
                String[] key = Fosdem.key(page);
                String raw = fetchFosdem(Fosdem.eventUrl(key[0], key[1]));
                Fosdem.Talk talk = Fosdem.event(raw);
                if (talk.media.isEmpty()) {
                    String head = raw.replace('\n', ' ');
                    if (head.length() > 120) {
                        head = head.substring(0, 120);
                    }
                    throw new java.io.IOException("no video: " + head);
                }
                java.util.ArrayList<Shelf.TrackIn> tracks = new java.util.ArrayList<>();
                for (Fosdem.Caption caption : talk.captions) {
                    try {
                        Cues.Transcript transcript = Cues.parse(fetchFosdem(caption.url), "vtt", false);
                        if (!transcript.spans.isEmpty()) {
                            String source = caption.label.toLowerCase(java.util.Locale.ROOT).contains("auto")
                                    ? "auto" : "manual";
                            tracks.add(new Shelf.TrackIn(
                                    caption.lang, source, blank(caption.label, "captions"), transcript));
                        }
                    } catch (Exception ignored) {
                        // A missing caption file still leaves the talk playable.
                    }
                }
                long saved = Shelf.save(
                        db, "FOSDEM", blank(talk.speaker, "FOSDEM"), blank(talk.speaker, "FOSDEM"),
                        "", talk.slug, blank(talk.title, page), blank(talk.page, page),
                        talk.media, tracks);
                Shelf.setThumb(db, saved, talk.thumb);
                Shelf.Video video = Shelf.video(db, saved);
                String note = tracks.isEmpty()
                        ? "No transcript. Keep, then Recognize."
                        : tracks.size() + " caption track" + (tracks.size() == 1 ? "" : "s");
                runOnUiThread(() -> {
                    if (videoId != saved) {
                        show(video, false);
                    }
                    play(talk.media, 0);
                    status.setText(note);
                });
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> status.setText(message));
            }
        });
    }

    private static String fetchFosdem(String url) throws java.io.IOException {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", OkDownloader.USER_AGENT)
                .header("Accept", "application/json,text/vtt,*/*")
                .build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new java.io.IOException("HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    private void loadOdysee(String url, String body, String label) {
        exploreStatus.setText("Loading");
        io.execute(() -> {
            try {
                List<Browse.Found> rows = new ArrayList<>();
                for (Odysee.Clip clip : Odysee.videos(postOdysee(url, body))) {
                    rows.add(new Browse.Found(
                            "video", clip.title, clip.page, clip.channel, clip.thumb));
                }
                runOnUiThread(() -> {
                    exploreSession = null;
                    exploreBack = Collections.emptyList();
                    showExplore(rows, label);
                });
            } catch (Exception e) {
                noteFeed(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void loadOdyseeChannel(String uri) {
        exploreStatus.setText("Loading");
        io.execute(() -> {
            try {
                Odysee.Clip channel = Odysee.resolved(postOdysee(
                        Odysee.PROXY + "resolve", Odysee.resolveBody(uri)));
                if (channel.id.isEmpty()) {
                    throw new java.io.IOException("no channel");
                }
                List<Browse.Found> rows = new ArrayList<>();
                for (Odysee.Clip clip : Odysee.videos(postOdysee(
                        Odysee.PROXY + "claim_search", Odysee.channelBody(channel.id)))) {
                    rows.add(new Browse.Found(
                            "video", clip.title, clip.page, clip.channel, clip.thumb));
                }
                String label = blank(channel.channel, "Odysee");
                runOnUiThread(() -> {
                    exploreSession = null;
                    exploreBack = Collections.emptyList();
                    showExplore(rows, label);
                });
            } catch (Exception e) {
                noteFeed(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void openOdysee(String uri, String page) {
        showVideo();
        exploreQuery.setText(page);
        status.setText("Opening");
        io.execute(() -> {
            try {
                Odysee.Clip clip = Odysee.resolved(postOdysee(
                        Odysee.PROXY + "resolve", Odysee.resolveBody(uri)));
                if (clip.id.isEmpty()) {
                    throw new java.io.IOException("no video");
                }
                if (clip.media.isEmpty()) {
                    throw new java.io.IOException("This video needs an Odysee login");
                }
                String channelKey = clip.channelId.isEmpty() ? clip.channel : clip.channelId;
                long saved = Shelf.save(
                        db, "Odysee", channelKey, blank(clip.channel, "Odysee"),
                        clip.channelPage, clip.id, blank(clip.title, page),
                        blank(clip.page, page), clip.media, List.of());
                Shelf.setThumb(db, saved, clip.thumb);
                Shelf.Video video = Shelf.video(db, saved);
                runOnUiThread(() -> {
                    if (videoId != saved) {
                        show(video, false);
                    }
                    play(clip.media, 0);
                    status.setText("No transcript. Keep, then Recognize.");
                });
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> status.setText(message));
            }
        });
    }

    private static String postOdysee(String url, String body) throws java.io.IOException {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", OkDownloader.USER_AGENT)
                .header("Content-Type", "application/json")
                .header("Origin", "https://odysee.com")
                .header("Referer", "https://odysee.com/")
                .post(okhttp3.RequestBody.create(body, okhttp3.MediaType.parse("application/json")))
                .build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new java.io.IOException("HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    private void loadKickHome() {
        exploreStatus.setText("Loading");
        io.execute(() -> {
            try {
                showKick(Kick.livestreams(fetchKick(Kick.HOME)), "Kick");
            } catch (Exception e) {
                noteFeed(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void loadKickSearch(String query) {
        exploreStatus.setText("Searching");
        io.execute(() -> {
            try {
                showKick(Kick.livestreams(fetchKick(Kick.searchUrl(query))), "Kick");
            } catch (Exception e) {
                noteFeed(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void showKick(List<Kick.Clip> clips, String label) {
        List<Browse.Found> rows = new ArrayList<>();
        for (Kick.Clip clip : clips) {
            rows.add(new Browse.Found("video", clip.title, clip.page, clip.author, clip.thumb));
        }
        runOnUiThread(() -> {
            exploreSession = null;
            exploreBack = Collections.emptyList();
            showExplore(rows, label);
        });
    }

    private void openKick(String page) {
        showVideo();
        exploreQuery.setText(page);
        status.setText("Opening");
        io.execute(() -> {
            try {
                String slug = Kick.slug(page);
                Kick.Clip clip = Kick.channel(fetchKick(Kick.channelUrl(slug)));
                if (clip.playback.isEmpty()) {
                    throw new java.io.IOException("Channel is not live");
                }
                long saved = Shelf.save(
                        db, "Kick", slug, blank(clip.author, slug), clip.page, slug,
                        blank(clip.title, slug), clip.page, clip.playback, List.of());
                Shelf.setThumb(db, saved, clip.thumb);
                Shelf.Video video = Shelf.video(db, saved);
                runOnUiThread(() -> {
                    if (videoId != saved) {
                        show(video, false);
                    }
                    play(clip.playback, 0);
                    status.setText("Live. No transcript.");
                });
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> status.setText(message));
            }
        });
    }

    private static String fetchKick(String url) throws java.io.IOException {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", OkDownloader.USER_AGENT)
                .header("Accept", "application/json")
                .header("Origin", "https://kick.com")
                .header("Referer", "https://kick.com/")
                .build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new java.io.IOException("HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    private void openRumble(String page) {
        openRumbleAt(page, 0);
    }

    private void openRumbleAt(String page, int position) {
        showVideo();
        exploreQuery.setText(page);
        status.setText("Opening");
        io.execute(() -> {
            try {
                String embed = Rumble.embedPage(Rumble.embedId(page) == null ? "" : Rumble.embedId(page));
                String title = page;
                String author = "Rumble";
                String authorUrl = "";
                String thumb = "";
                if (Rumble.embedId(page) == null) {
                    Rumble.Clip clip = Rumble.oembed(fetchRumble(Rumble.oembedUrl(page)));
                    if (clip.embed.isEmpty()) {
                        throw new java.io.IOException("no embed");
                    }
                    embed = clip.embed;
                    title = clip.title;
                    author = clip.author;
                    authorUrl = clip.authorUrl;
                    thumb = clip.thumb;
                }
                String html = fetchRumble(embed);
                if (Rumble.challenged(html)) {
                    throw new java.io.IOException("blocked");
                }
                if (Rumble.embedId(page) != null) {
                    title = blank(Rumble.pageTitle(html), page);
                }
                String media = Rumble.choose(Rumble.streams(html), qualityHeight);
                if (media.isEmpty()) {
                    throw new java.io.IOException("no stream");
                }
                String id = Rumble.embedId(embed);
                long saved = Shelf.save(
                        db, "Rumble", blank(authorUrl, author), blank(author, "Rumble"),
                        authorUrl, blank(id, page), blank(title, page), page, media, List.of());
                Shelf.setThumb(db, saved, thumb);
                Shelf.Video video = Shelf.video(db, saved);
                runOnUiThread(() -> {
                    if (videoId != saved) {
                        show(video, false);
                    }
                    play(media, position);
                    status.setText("No transcript. Keep, then Recognize.");
                });
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> status.setText(message));
            }
        });
    }

    private static String fetchRumble(String url) throws java.io.IOException {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", Nico.UA)
                .header("Referer", "https://rumble.com/")
                .header("Accept", "text/html,application/json")
                .build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new java.io.IOException("HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    private void loadNicoHome() {
        exploreStatus.setText("Loading");
        io.execute(() -> {
            try {
                showNico(Nico.videos(fetchNico(Nico.HOME).body), "Niconico");
            } catch (Exception e) {
                noteFeed(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void loadNicoSearch(String query) {
        exploreStatus.setText("Searching");
        io.execute(() -> {
            try {
                showNico(Nico.videos(fetchNico(Nico.searchUrl(query)).body), "Niconico");
            } catch (Exception e) {
                noteFeed(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void loadNicoUser(String id) {
        exploreStatus.setText("Loading");
        io.execute(() -> {
            try {
                showNico(Nico.videos(fetchNico(Nico.userUrl(id)).body), "Niconico");
            } catch (Exception e) {
                noteFeed(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void showNico(List<Nico.Clip> clips, String label) {
        List<Browse.Found> rows = new ArrayList<>();
        for (Nico.Clip clip : clips) {
            rows.add(new Browse.Found("video", clip.title, clip.page, clip.channel, clip.thumb));
        }
        runOnUiThread(() -> {
            exploreSession = null;
            exploreBack = Collections.emptyList();
            showExplore(rows, label);
        });
    }

    private void openNico(String page) {
        showVideo();
        exploreQuery.setText(page);
        status.setText("Opening");
        playNico(page, 0);
    }

    private void playNico(String page, int position) {
        io.execute(() -> {
            try {
                NicoPage watched = fetchNico(page);
                String cookie = watched.cookie.isEmpty() ? PlayService.nicoCookie : watched.cookie;
                Nico.Watch watch = Nico.watch(watched.body);
                String[] chosen = Nico.pair(watch, qualityHeight);
                if (chosen[0].isEmpty()) {
                    throw new java.io.IOException("no stream");
                }
                String hls = Nico.contentUrl(postNico(
                        Nico.hlsUrl(watch.clip.id, watch.trackId),
                        Nico.hlsBody(chosen[0], chosen[1]),
                        watch.accessKey,
                        cookie));
                if (hls.isEmpty()) {
                    throw new java.io.IOException("no stream");
                }
                PlayService.nicoCookie = cookie;
                String channelKey = watch.clip.channelId.isEmpty() ? watch.clip.channel : watch.clip.channelId;
                long saved = Shelf.save(
                        db, "Niconico", channelKey, blank(watch.clip.channel, "Niconico"),
                        watch.clip.channelPage, watch.clip.id, blank(watch.clip.title, page),
                        watch.clip.page, hls, List.of());
                Shelf.setThumb(db, saved, watch.clip.thumb);
                Shelf.Video video = Shelf.video(db, saved);
                String blurb = watch.clip.description;
                runOnUiThread(() -> {
                    if (videoId != saved) {
                        show(video, false);
                    }
                    if (!blurb.isEmpty()) {
                        description.setText(blurb);
                    }
                    play(hls, position);
                    status.setText("No transcript. Keep, then Recognize.");
                });
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> status.setText(message));
            }
        });
    }

    private static final class NicoPage {
        final String body;
        final String cookie;

        NicoPage(String body, String cookie) {
            this.body = body;
            this.cookie = cookie;
        }
    }

    private static NicoPage fetchNico(String url) throws java.io.IOException {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", Nico.UA)
                .header("Accept-Language", "ja,en;q=0.7")
                .header("Referer", "https://www.nicovideo.jp/")
                .header("X-Frontend-Id", "6")
                .build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new java.io.IOException("HTTP " + response.code());
            }
            String cookie = "";
            for (String header : response.headers("Set-Cookie")) {
                String found = Nico.cookie(header);
                if (!found.isEmpty()) {
                    cookie = found;
                }
            }
            return new NicoPage(response.body().string(), cookie);
        }
    }

    private static String postNico(String url, String body, String key, String cookie)
            throws java.io.IOException {
        okhttp3.Request.Builder request = new okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", Nico.UA)
                .header("Content-Type", "application/json")
                .header("x-access-right-key", key)
                .header("x-request-with", "nicovideo")
                .header("x-frontend-id", "6")
                .header("Referer", "https://www.nicovideo.jp/");
        if (cookie != null && !cookie.isEmpty()) {
            request.header("Cookie", cookie);
        }
        okhttp3.Request built = request
                .post(okhttp3.RequestBody.create(body, okhttp3.MediaType.parse("application/json")))
                .build();
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        try (okhttp3.Response response = client.newCall(built).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new java.io.IOException("HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    private void loadBiliHome() {
        exploreStatus.setText("Loading");
        io.execute(() -> {
            try {
                ensureBili();
                showBili(Bili.home(fetchText(Bili.HOME, biliBuvid)), "Bilibili");
            } catch (Exception e) {
                noteFeed(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void loadBiliSearch(String query) {
        exploreStatus.setText("Searching");
        io.execute(() -> {
            try {
                ensureBili();
                String body = fetchText(Bili.searchUrl(query, biliMixin, now()), biliBuvid);
                showBili(Bili.search(body), "Bilibili");
            } catch (Exception e) {
                noteFeed(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void loadBiliSpace(String mid) {
        exploreStatus.setText("Loading");
        io.execute(() -> {
            try {
                ensureBili();
                String body = fetchText(Bili.spaceUrl(mid, biliMixin, now()), biliBuvid);
                showBili(Bili.space(body), "Bilibili");
            } catch (Exception e) {
                noteFeed(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void showBili(List<Bili.Clip> clips, String label) {
        List<Browse.Found> rows = new ArrayList<>();
        for (Bili.Clip clip : clips) {
            rows.add(new Browse.Found("video", clip.title, clip.page, clip.channel, clip.thumb));
        }
        runOnUiThread(() -> {
            exploreSession = null;
            exploreBack = Collections.emptyList();
            showExplore(rows, label);
        });
    }

    private void openBili(String page) {
        showVideo();
        exploreQuery.setText(page);
        status.setText("Opening");
        playBili(page, 0);
    }

    /** Refetches the picture and audio and starts them together. */
    private void playBili(String page, int position) {
        io.execute(() -> {
            try {
                long saved = saveBili(page);
                Bili.Pair pair = lastBili;
                Shelf.Video video = Shelf.video(db, saved);
                String blurb = lastBiliNote;
                runOnUiThread(() -> {
                    if (videoId != saved) {
                        show(video, false);
                    }
                    if (!blurb.isEmpty()) {
                        description.setText(blurb);
                    }
                    if (pair.video.isEmpty()) {
                        play(pair.audio, position);
                    } else if (pair.audio.isEmpty()) {
                        play(pair.video, position);
                    } else {
                        playPair(pair.video, pair.audio, position);
                    }
                    status.setText("No transcript. Keep, then Recognize.");
                });
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> status.setText(message));
            }
        });
    }

    private Bili.Pair lastBili = new Bili.Pair("", "");
    private String lastBiliNote = "";

    private long saveBili(String page) throws java.io.IOException {
        ensureBili();
        String id = Bili.bvid(page);
        Bili.Clip clip = Bili.video(fetchText(Bili.viewUrl(id), biliBuvid));
        if (clip.cid.isEmpty()) {
            throw new java.io.IOException("no Bilibili video");
        }
        String play = fetchText(Bili.playUrl(id, clip.cid, biliMixin, now()), biliBuvid);
        Bili.Pair pair = Bili.choose(play, qualityHeight);
        if (pair.video.isEmpty() && pair.audio.isEmpty()) {
            throw new java.io.IOException("no stream");
        }
        lastBili = pair;
        lastBiliNote = clip.description;
        String stored = pair.video.isEmpty() ? pair.audio : pair.video;
        String channelKey = clip.channelId.isEmpty() ? clip.channel : clip.channelId;
        long saved = Shelf.save(
                db, "Bilibili", channelKey, blank(clip.channel, "Bilibili"),
                clip.channelPage, clip.id, blank(clip.title, page), clip.page, stored, List.of());
        Shelf.setThumb(db, saved, clip.thumb);
        return saved;
    }

    /** The separate audio file for a Bilibili video, used when the kept picture has no sound. */
    private String biliAudio(String page) throws java.io.IOException {
        ensureBili();
        String id = Bili.bvid(page);
        Bili.Clip clip = Bili.video(fetchText(Bili.viewUrl(id), biliBuvid));
        if (clip.cid.isEmpty()) {
            return "";
        }
        String play = fetchText(Bili.playUrl(id, clip.cid, biliMixin, now()), biliBuvid);
        return Bili.choose(play, 0).audio;
    }

    private void ensureBili() throws java.io.IOException {
        if (!biliMixin.isEmpty() && !biliBuvid.isEmpty()) {
            return;
        }
        String mixin = Bili.mixinKey(fetchText(Bili.NAV), fetchText(Bili.HEADER));
        String buvid = Bili.buvid(fetchText(Bili.SPI));
        if (mixin.isEmpty() || buvid.isEmpty()) {
            throw new java.io.IOException("no Bilibili key");
        }
        biliMixin = mixin;
        biliBuvid = buvid;
    }

    private static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    private static String fetchText(String url) throws java.io.IOException {
        return fetchText(url, "");
    }

    private static String fetchText(String url, String buvid) throws java.io.IOException {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        okhttp3.Request.Builder request = new okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", OkDownloader.USER_AGENT);
        if (buvid != null && !buvid.isEmpty()) {
            request.header("Cookie", "buvid3=" + buvid);
            request.header("Referer", "https://www.bilibili.com/");
        }
        okhttp3.Request built = request.build();
        try (okhttp3.Response response = client.newCall(built).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new java.io.IOException("HTTP " + response.code());
            }
            String body = response.body().string();
            if (body.length() > 3_000_000) {
                return body.substring(0, 3_000_000);
            }
            return body;
        }
    }

    /** Saves a stream. Does not record the path on the shelf. */
    private void download(String source, java.io.File dest, String label) throws java.io.IOException {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder()
                .url(source)
                .header("User-Agent", OkDownloader.USER_AGENT);
        if (source.contains("bilivideo") || source.contains("upgcxcode")) {
            requestBuilder.header("Referer", "https://www.bilibili.com/");
        }
        if (source.contains("rumble.cloud")) {
            requestBuilder.header("Referer", "https://rumble.com/");
        }
        if (source.contains("odycdn.com")) {
            requestBuilder.header("Referer", "https://odysee.com/");
            requestBuilder.header("Origin", "https://odysee.com");
        }
        if (source.contains("ted.com")) {
            requestBuilder.header("Referer", "https://www.ted.com/");
        }
        okhttp3.Request request = requestBuilder.build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new java.io.IOException("HTTP " + response.code());
            }
            try (java.io.InputStream in = response.body().byteStream();
                 java.io.OutputStream out = new java.io.FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int n;
                long got = 0;
                long reported = -1;
                while ((n = in.read(buf)) >= 0) {
                    out.write(buf, 0, n);
                    got += n;
                    long mb = got / (1024 * 1024);
                    if (mb != reported) {
                        reported = mb;
                        runOnUiThread(() -> status.setText(label + " " + mb + " MB"));
                    }
                }
            }
        } catch (Exception e) {
            dest.delete();
            if (e instanceof java.io.IOException) {
                throw (java.io.IOException) e;
            }
            throw new java.io.IOException(e.getMessage(), e);
        }
    }

    private void fillExploreServices() {
        if (!exploreServices.isEmpty()) {
            return;
        }
        exploreServices = new ArrayList<>(NewPipe.getServices());
        List<String> names = new ArrayList<>();
        for (StreamingService service : exploreServices) {
            names.add(service.getServiceInfo().getName());
        }
        names.add("BitChute");
        names.add("Bilibili");
        names.add("Niconico");
        names.add("Rumble");
        names.add("Kick");
        names.add("Odysee");
        names.add("FOSDEM");
        names.add("Internet Archive");
        names.add("TED");
        names.add("NASA+");
        names.add("Red Bull TV");
        exploreService.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (filling) {
                    return;
                }
                if (sharedOpen) {
                    sharedOpen = false;
                    return;
                }
                if (exploreQuery.getText().toString().trim().isEmpty()
                        && !videoOpen && section == 0) {
                    loadFeed();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        filling = true;
        exploreService.setAdapter(spinner(names));
        filling = false;
    }

    private void exploreSearch() {
        String query = exploreQuery.getText().toString().trim();
        if (query.startsWith("www.")) {
            query = "https://" + query;
        }
        if (query.isEmpty() || exploreServices.isEmpty()) {
            if (exploreServices.isEmpty()) {
                return;
            }
            loadFeed();
            return;
        }
        if (Archive.identifier(query) != null) {
            openArchive(query, 0);
            return;
        }
        if (Ted.slug(query) != null) {
            openTed(query, 0);
            return;
        }
        if (Chute.videoId(query) != null) {
            openChute(query);
            return;
        }
        if (Bili.bvid(query) != null) {
            openBili(query);
            return;
        }
        if (Nico.videoId(query) != null) {
            openNico(query);
            return;
        }
        if (Rumble.embedId(query) != null || Rumble.isWatch(query)) {
            openRumble(query);
            return;
        }
        if (Kick.slug(query) != null) {
            openKick(query);
            return;
        }
        String odyseeVideo = Odysee.videoUri(query);
        if (odyseeVideo != null) {
            openOdysee(odyseeVideo, query);
            return;
        }
        if (Fosdem.key(query) != null) {
            openFosdem(query);
            return;
        }
        if (Nasa.slug(query) != null) {
            openNasa(query);
            return;
        }
        if (RedBull.id(query) != null) {
            openRedBull(query);
            return;
        }
        exploreStatus.setText("Searching");
        String typed = query;
        int which = Math.max(exploreService.getSelectedItemPosition(), 0);
        io.execute(() -> {
            try {
                String chuteChannel = Chute.channelId(typed);
                if (chuteChannel != null) {
                    loadChute(Chute.CHANNEL, Chute.channelBody(chuteChannel), "BitChute");
                    return;
                }
                if (chuteSelected() && !typed.contains("://")) {
                    loadChute(Chute.SEARCH, Chute.searchBody(typed), "BitChute");
                    return;
                }
                String biliChannel = Bili.mid(typed);
                if (biliChannel != null) {
                    loadBiliSpace(biliChannel);
                    return;
                }
                if (biliSelected() && !typed.contains("://")) {
                    loadBiliSearch(typed);
                    return;
                }
                String nicoUser = Nico.userId(typed);
                if (nicoUser != null) {
                    loadNicoUser(nicoUser);
                    return;
                }
                if (nicoSelected() && !typed.contains("://")) {
                    loadNicoSearch(typed);
                    return;
                }
                if (rumbleSelected() && !typed.contains("://")) {
                    noteFeed("Paste a Rumble link. Search is blocked here.");
                    return;
                }
                if (kickSelected() && !typed.contains("://")) {
                    loadKickSearch(typed);
                    return;
                }
                String odyseeChannel = Odysee.channelUri(typed);
                if (odyseeChannel != null) {
                    loadOdyseeChannel(odyseeChannel);
                    return;
                }
                if (odyseeSelected() && !typed.contains("://")) {
                    loadOdysee(Odysee.PROXY + "claim_search", Odysee.searchBody(typed), "Odysee");
                    return;
                }
                if (fosdemSelected() && !typed.contains("://")) {
                    loadFosdem(Fosdem.searchUrl(typed), "FOSDEM");
                    return;
                }
                if (archiveSelected() && !typed.contains("://")) {
                    loadArchive(Archive.searchUrl(typed), "Internet Archive");
                    return;
                }
                if (tedSelected() && !typed.contains("://")) {
                    loadTedSearch(typed);
                    return;
                }
                if (nasaSelected() && !typed.contains("://")) {
                    loadNasa(Nasa.searchUrl(typed), "NASA+");
                    return;
                }
                if (redBullSelected() && !typed.contains("://")) {
                    loadRedBull(RedBull.searchUrl(typed), "Red Bull TV");
                    return;
                }
                int serviceAt = which < exploreServices.size() ? which : 0;
                StreamingService service = exploreServices.get(serviceAt);
                StreamingService.LinkType link = null;
                try {
                    StreamingService byUrl = NewPipe.getServiceByUrl(typed);
                    link = byUrl.getLinkTypeByUrl(typed);
                    service = byUrl;
                } catch (Exception ignored) {
                    link = null;
                }
                if (typed.contains("<opml") || typed.contains("xmlUrl=")) {
                    restoreFeeds(typed);
                    return;
                }
                if (link == null && looksLikeFeed(typed)) {
                    if (rssPanel) {
                        openRss(typed);
                    } else {
                        openFeed(typed);
                    }
                    return;
                }
                if (link == StreamingService.LinkType.STREAM
                        || (link == null && typed.contains("://"))) {
                    runOnUiThread(() -> openVideo(typed));
                    return;
                }
                if (link == StreamingService.LinkType.CHANNEL) {
                    Browse.Session session = Browse.channel(service, typed);
                    String label = service.getServiceInfo().getName();
                    runOnUiThread(() -> showSession(session, label));
                    return;
                }
                if (link == StreamingService.LinkType.PLAYLIST) {
                    Browse.Session session = Browse.playlist(service, typed);
                    runOnUiThread(() -> showSession(session, "Playlist"));
                    return;
                }
                Browse.Session session = Browse.search(service, typed);
                String label = service.getServiceInfo().getName();
                runOnUiThread(() -> {
                    exploreBack = Collections.emptyList();
                    showSession(session, label);
                });
            } catch (Exception e) {
                noteFeed(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void showSession(Browse.Session session, String label) {
        exploreSession = session;
        showExplore(session == null ? Collections.emptyList() : session.items, label);
    }

    /** A feed that cannot be listed. The previous rows are cleared. */
    private void noteFeed(String message) {
        runOnUiThread(() -> {
            feedEpisodes = Collections.emptyList();
            feedAt = 0;
            linkRows = Collections.emptyList();
            linkAt = 0;
            armMore(false);
            exploreRows = Collections.emptyList();
            exploreSession = null;
            episodes = Collections.emptyList();
            exploreStatus.setText(message);
            exploreList.setAdapter(new ExploreAdapter());
            showTab(0);
        });
    }

    private void showFeedPage() {
        int end = Math.min(feedAt + Cast.PAGE, feedEpisodes.size());
        List<Cast.Episode> page = feedAt >= end
                ? Collections.emptyList()
                : new ArrayList<>(feedEpisodes.subList(feedAt, end));
        List<Browse.Found> rows = new ArrayList<>();
        for (Cast.Episode episode : page) {
            rows.add(new Browse.Found("episode", episode.title, episode.audio, feedLabel));
        }
        episodes = page;
        linkRows = Collections.emptyList();
        exploreRows = rows;
        exploreSession = null;
        armMore(false);
        String more = end < feedEpisodes.size() ? " · more" : "";
        exploreStatus.setText(feedLabel + " · " + rows.size() + more);
        exploreList.setAdapter(new ExploreAdapter());
        showTab(0);
    }

    private void armMore(boolean share) {
        shareCollection = share;
        ((Button) findViewById(R.id.explore_more)).setText(share ? R.string.share : R.string.more);
    }

    private void showExplore(List<Browse.Found> rows, String label) {
        feedEpisodes = Collections.emptyList();
        feedAt = 0;
        linkRows = Collections.emptyList();
        linkAt = 0;
        armMore(false);
        exploreRows = rows;
        boolean anyEpisode = false;
        for (Browse.Found row : rows) {
            if ("episode".equals(row.kind)) {
                anyEpisode = true;
                break;
            }
        }
        if (!anyEpisode) {
            episodes = Collections.emptyList();
        }
        String more = exploreSession != null && exploreSession.next != null ? " · more" : "";
        exploreStatus.setText(label + " · " + rows.size() + more);
        exploreList.setAdapter(new ExploreAdapter());
        showTab(0);
    }

    private void more() {
        if (shareCollection) {
            shareFeeds();
            return;
        }
        if (!linkRows.isEmpty() && linkAt + Cast.PAGE < linkRows.size()) {
            linkAt += Cast.PAGE;
            showLinkPage();
            return;
        }
        if (!feedEpisodes.isEmpty() && feedAt + Cast.PAGE < feedEpisodes.size()) {
            feedAt += Cast.PAGE;
            showFeedPage();
            return;
        }
        if (exploreSession == null || exploreSession.next == null) {
            exploreStatus.setText("No further page");
            return;
        }
        exploreStatus.setText("Next page");
        io.execute(() -> {
            try {
                Browse.Session session = Browse.more(exploreSession);
                runOnUiThread(() -> showSession(session, "More"));
            } catch (Exception e) {
                String missed = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> exploreStatus.setText(missed));
            }
        });
    }

    private void checkFeed() {
        exploreStatus.setText("Checking");
        io.execute(() -> {
            List<Browse.Found> fresh = new ArrayList<>();
            List<Cast.Episode> heard = new ArrayList<>();
            for (String[] row : Shelf.checkRows(db)) {
                if ("RSS".equals(row[1])) {
                    continue;
                }
                if ("Podcast".equals(row[1])) {
                    try {
                        String body = fetchText(row[2]);
                        if (body.contains("<rss") || body.contains("<feed")) {
                            Cast.Show show = Cast.parse(body);
                            String label = show.title.isEmpty() ? row[3] : show.title;
                            int seen = 0;
                            for (Cast.Episode episode : show.episodes) {
                                if (seen == Cast.PAGE) {
                                    break;
                                }
                                seen++;
                                feedOf.put(episode.audio, row[2]);
                                if (Shelf.known(db, episode.audio) || Shelf.known(db, episode.page)) {
                                    continue;
                                }
                                fresh.add(new Browse.Found(
                                        "episode", episode.title, episode.audio, label));
                                heard.add(episode);
                            }
                        }
                    } catch (Exception ignored) {
                        // One feed failing leaves the others.
                    }
                    continue;
                }
                try {
                    StreamingService service = NewPipe.getServiceByUrl(row[2]);
                    Browse.Session session = "playlist".equals(row[0])
                            ? Browse.playlist(service, row[2])
                            : Browse.channel(service, row[2]);
                    for (Browse.Found item : session.items) {
                        if ("video".equals(item.kind) && !Shelf.known(db, item.url)) {
                            fresh.add(item);
                        }
                    }
                } catch (Exception ignored) {
                    // One bookmark failing leaves the others.
                }
            }
            if (fresh.isEmpty()) {
                runOnUiThread(() -> {
                    episodes = heard;
                    exploreSession = null;
                    showExplore(fresh, "No new videos");
                });
                return;
            }
            int withText = 0;
            int failed = 0;
            int episodeCount = 0;
            for (int i = 0; i < fresh.size(); i++) {
                if ("episode".equals(fresh.get(i).kind)) {
                    episodeCount++;
                    continue;
                }
                int at = i + 1;
                int total = fresh.size();
                runOnUiThread(() -> exploreStatus.setText("Check " + at + " of " + total));
                try {
                    if (capture(fresh.get(i).url, false).captions) {
                        withText++;
                    }
                } catch (Exception ignored) {
                    failed++;
                }
            }
            int found = withText;
            int missed = failed;
            int shows = episodeCount;
            String label = found == 0 && shows > 0
                    ? shows + " new episode" + (shows == 1 ? "" : "s")
                    : "Checked " + found + " with captions"
                    + (shows > 0 ? " · " + shows + " episodes" : "");
            runOnUiThread(() -> {
                episodes = heard;
                exploreSession = null;
                showExplore(fresh, label);
                if (missed > 0) {
                    exploreStatus.setText(exploreStatus.getText() + " · " + missed + " failed");
                }
            });
        });
    }

    private void showBookmarks() {
        List<Browse.Found> rows = new ArrayList<>();
        for (String[] row : Shelf.bookmarkRows(db)) {
            rows.add(new Browse.Found(row[0], row[3], row[2], row[1]));
        }
        exploreBack = new ArrayList<>(exploreRows);
        exploreSession = null;
        showExplore(rows, "Bookmarks");
    }

    private void showSavedFeeds(String service) {
        rssPanel = "RSS".equals(service);
        List<Browse.Found> rows = new ArrayList<>();
        for (String[] row : Shelf.follows(db)) {
            if (service.equals(row[1])) {
                rows.add(new Browse.Found(row[0], row[3], row[2], row[1]));
            }
        }
        showTab(0);
        showExplore(rows, service);
        shareCollection = true;
        armMore(true);
        if (rows.isEmpty()) {
            exploreStatus.setText("RSS".equals(service)
                    ? "Paste an RSS feed" : "Paste a podcast feed");
        }
    }

    private void shareFeeds() {
        List<String[]> feeds = new ArrayList<>();
        for (String[] row : Shelf.follows(db)) {
            if ("RSS".equals(row[1]) || "Podcast".equals(row[1])) {
                feeds.add(new String[] {row[1], row[3], row[2]});
            }
        }
        if (feeds.isEmpty()) {
            exploreStatus.setText("No feeds to share");
            return;
        }
        android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(android.content.Intent.EXTRA_SUBJECT, "CuePipe feeds");
        send.putExtra(android.content.Intent.EXTRA_TEXT, Cast.opml(feeds));
        startActivity(android.content.Intent.createChooser(send, getString(R.string.share)));
    }

    private void restoreFeeds(String note) {
        try {
            List<String[]> feeds = Cast.outlines(note);
            if (feeds.isEmpty()) {
                noteFeed("No feeds in that note");
                return;
            }
            for (String[] feed : feeds) {
                Shelf.follow(db, feed[0], feed[2], feed[1], true);
            }
            int count = feeds.size();
            runOnUiThread(() -> {
                showFollows();
                exploreStatus.setText(count + " feeds restored");
            });
        } catch (Exception e) {
            noteFeed(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void openLink(String url) {
        try {
            startActivity(new android.content.Intent(
                    android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (Exception e) {
            shareText("", url);
        }
    }

    private void shareEpisode(Browse.Found item) {
        String page = item.url;
        for (List<Cast.Episode> list : new List[] {episodes, feedEpisodes}) {
            for (Cast.Episode episode : list) {
                if (item.url.equals(episode.audio) && episode.page != null && !episode.page.isEmpty()) {
                    page = episode.page;
                    break;
                }
            }
        }
        String text = item.name + "\n" + (page.equals(item.url) ? item.url : page + "\n" + item.url);
        shareText(item.name, text);
    }

    private void shareWatching() {
        String name = title.getText().toString();
        String page = pageUrl == null ? "" : pageUrl;
        String audio = playUrl == null ? "" : playUrl;
        if (page.isEmpty() && audio.isEmpty()) {
            status.setText("Nothing to share");
            return;
        }
        String text = name;
        if (!page.isEmpty()) {
            text = text + "\n" + page;
        }
        if (!audio.isEmpty() && !audio.equals(page)) {
            text = text + "\n" + audio;
        }
        shareText(name, text);
    }

    private void shareText(String subject, String text) {
        android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
        send.setType("text/plain");
        if (subject != null && !subject.isEmpty()) {
            send.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);
        }
        send.putExtra(android.content.Intent.EXTRA_TEXT, text);
        startActivity(android.content.Intent.createChooser(send, getString(R.string.share)));
    }

    private void showFollows() {
        List<Browse.Found> rows = new ArrayList<>();
        for (String[] row : Shelf.follows(db)) {
            rows.add(new Browse.Found(row[0], row[3], row[2], row[1]));
        }
        exploreBack = new ArrayList<>(exploreRows);
        exploreSession = null;
        showExplore(rows, "Subscriptions");
    }

    private void toggleFollow() {
        Shelf.Video video = Shelf.video(db, videoId);
        String url = Shelf.channelUrl(db, videoId);
        if (video == null || url.isEmpty()) {
            status.setText("This channel has no page");
            return;
        }
        boolean on = !Shelf.followed(db, url);
        Shelf.follow(db, video.service, url, video.channel, on);
        status.setText(on ? "Following" : "Unfollowed");
        paintFollow();
    }

    private void paintFollow() {
        Button follow = findViewById(R.id.follow);
        String url = Shelf.channelUrl(db, videoId);
        follow.setText(!url.isEmpty() && Shelf.followed(db, url)
                ? R.string.following : R.string.follow);
    }

    private void openExplore(int position) {
        if (position < 0 || position >= exploreRows.size()) {
            return;
        }
        Browse.Found item = exploreRows.get(position);
        if ("episode".equals(item.kind)) {
            openEpisode(item.url);
            return;
        }
        if ("link".equals(item.kind)) {
            openLink(item.url);
            return;
        }
        if ("RSS".equals(item.meta)) {
            exploreStatus.setText("Opening");
            String url = item.url;
            io.execute(() -> openRss(url));
            return;
        }
        if ("Podcast".equals(item.meta)) {
            exploreStatus.setText("Opening");
            String url = item.url;
            io.execute(() -> openFeed(url));
            return;
        }
        if (Chute.videoId(item.url) != null) {
            openChute(item.url);
            return;
        }
        if (Bili.bvid(item.url) != null) {
            openBili(item.url);
            return;
        }
        if (Nico.videoId(item.url) != null) {
            openNico(item.url);
            return;
        }
        if (Rumble.embedId(item.url) != null || Rumble.isWatch(item.url)) {
            openRumble(item.url);
            return;
        }
        if (Kick.slug(item.url) != null) {
            openKick(item.url);
            return;
        }
        String odyseeVideo = Odysee.videoUri(item.url);
        if (odyseeVideo != null) {
            openOdysee(odyseeVideo, item.url);
            return;
        }
        if (Fosdem.key(item.url) != null) {
            openFosdem(item.url);
            return;
        }
        if (Archive.identifier(item.url) != null) {
            openArchive(item.url, 0);
            return;
        }
        if (Ted.slug(item.url) != null) {
            openTed(item.url, 0);
            return;
        }
        if (Nasa.slug(item.url) != null) {
            openNasa(item.url);
            return;
        }
        if (RedBull.id(item.url) != null) {
            openRedBull(item.url);
            return;
        }
        if ("video".equals(item.kind)) {
            openVideo(item.url);
            return;
        }
        exploreStatus.setText("Opening");
        List<Browse.Found> previous = new ArrayList<>(exploreRows);
        io.execute(() -> {
            try {
                StreamingService service = NewPipe.getServiceByUrl(item.url);
                Browse.Session session = "playlist".equals(item.kind)
                        ? Browse.playlist(service, item.url)
                        : Browse.channel(service, item.url);
                runOnUiThread(() -> {
                    exploreBack = previous;
                    showSession(session, item.name);
                });
            } catch (Exception e) {
                String missed = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> exploreStatus.setText(missed));
            }
        });
    }

    private void openVideo(String page) {
        showVideo();
        exploreQuery.setText(page);
        fetch(page);
    }

    private void toggleBookmark(int position) {
        if (position < 0 || position >= exploreRows.size() || exploreServices.isEmpty()) {
            return;
        }
        Browse.Found item = exploreRows.get(position);
        if (Shelf.bookmarked(db, item.url)) {
            Shelf.forget(db, item.url);
            exploreStatus.setText("Removed bookmark");
            return;
        }
        int which = Math.max(exploreService.getSelectedItemPosition(), 0);
        String service = which < exploreServices.size()
                ? exploreServices.get(which).getServiceInfo().getName()
                : which == exploreServices.size() ? "BitChute"
                : which == exploreServices.size() + 1 ? "Bilibili"
                : which == exploreServices.size() + 2 ? "Niconico"
                : which == exploreServices.size() + 3 ? "Rumble"
                : which == exploreServices.size() + 4 ? "Kick"
                : which == exploreServices.size() + 5 ? "Odysee"
                : which == exploreServices.size() + 6 ? "FOSDEM"
                : which == exploreServices.size() + 7 ? "Internet Archive"
                : which == exploreServices.size() + 8 ? "TED"
                : which == exploreServices.size() + 9 ? "NASA+" : "Red Bull TV";
        Shelf.bookmark(db, item.kind, service, item.url, item.name);
        exploreStatus.setText("Bookmarked");
    }

    private void lineMenu(int position) {
        int t0;
        String text;
        int byteOffset;
        long trackId;
        boolean canEdit = false;
        if (prose) {
            if (position < 0 || position >= blocks.size() || tracks.isEmpty()) {
                return;
            }
            Prose.Block block = blocks.get(position);
            if (block.spanIndex < 0 || block.spanIndex >= spans.size()) {
                return;
            }
            t0 = block.t0;
            text = block.text;
            byteOffset = spans.get(block.spanIndex).byteStart;
            int primary = chosen(0);
            trackId = tracks.get(primary < 0 ? 0 : primary).id;
        } else {
            if (position < 0 || position >= spans.size() || chosen(0) < 0) {
                return;
            }
            Cues.Span span = spans.get(position);
            t0 = span.t0;
            text = span.text;
            byteOffset = span.byteStart;
            trackId = tracks.get(chosen(0)).id;
            canEdit = true;
        }
        String[] actions = canEdit
                ? new String[] {"Play here", "Save excerpt", "Share", "Edit line"}
                : new String[] {"Play here", "Save excerpt", "Share"};
        int playAt = t0;
        String quote = text;
        int bytes = byteOffset;
        long savedTrack = trackId;
        int line = position;
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(stamp(t0))
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        playHere(playAt);
                    } else if (which == 1) {
                        saveExcerpt(savedTrack, playAt, bytes, quote);
                    } else if (which == 2) {
                        shareExcerpt(quote, playAt);
                    } else {
                        editLine(line);
                    }
                })
                .show();
    }

    private void playHere(int t0) {
        int at = Math.max(0, t0 + offsetMs);
        if (player.getMediaItemCount() > 0
                && player.getPlaybackState() != Player.STATE_IDLE) {
            seek(at);
        } else {
            jump(at);
        }
    }

    /** Video time minus the subtitle delay, so a late file can be shown early. */
    private int cueClock() {
        return (int) player.getCurrentPosition() - offsetMs;
    }

    /** Shrinks the picture so the transcript keeps the page. */
    private void setMini(boolean on) {
        mini = on;
        android.widget.FrameLayout frame = findViewById(R.id.player_frame);
        android.widget.LinearLayout.LayoutParams lp =
                (android.widget.LinearLayout.LayoutParams) frame.getLayoutParams();
        if (on) {
            lp.width = dp(168);
            lp.height = dp(94);
            lp.gravity = android.view.Gravity.END;
            frame.setElevation(dp(6));
        } else {
            lp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = dp(200);
            lp.gravity = android.view.Gravity.NO_GRAVITY;
            frame.setElevation(0);
        }
        frame.setLayoutParams(lp);
        ((Button) findViewById(R.id.mini)).setText(on ? R.string.full : R.string.mini);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void playerMenu() {
        String[] items = {
                "Speed " + speedLabel(speed),
                "Subtitle offset " + offsetLabel(offsetMs),
                "Quality " + qualityLabel(qualityHeight),
                mini ? "Full" : "Mini",
                "Keep",
                "Burned-in",
                "Recognize",
                onScreen.isChecked() ? "On screen off" : "On screen",
                "Translate",
                relatedMode ? "Transcript" : "Related",
                "Details"
        };
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.player)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        pickSpeed();
                    } else if (which == 1) {
                        pickOffset();
                    } else if (which == 2) {
                        pickQuality();
                    } else if (which == 3) {
                        setMini(!mini);
                    } else if (which == 4) {
                        keep();
                    } else if (which == 5) {
                        burned();
                    } else if (which == 6) {
                        recognize();
                    } else if (which == 7) {
                        onScreen.setChecked(!onScreen.isChecked());
                    } else if (which == 8) {
                        translate();
                    } else if (which == 9) {
                        findViewById(relatedMode ? R.id.show_transcript : R.id.show_related).performClick();
                    } else {
                        findViewById(R.id.details).performClick();
                    }
                })
                .show();
    }

    private void pickSpeed() {
        float[] rates = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
        String[] labels = new String[rates.length];
        for (int i = 0; i < rates.length; i++) {
            labels[i] = speedLabel(rates[i]);
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Speed")
                .setItems(labels, (dialog, which) -> {
                    speed = rates[which];
                    player.setPlaybackSpeed(speed);
                    status.setText("Speed " + speedLabel(speed));
                })
                .show();
    }

    private void pickQuality() {
        int[] heights = {-1, 360, 720, 0};
        String[] labels = new String[heights.length];
        for (int i = 0; i < heights.length; i++) {
            labels[i] = qualityLabel(heights[i]);
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Quality")
                .setItems(labels, (dialog, which) -> applyQuality(heights[which]))
                .show();
    }

    /** Switches the stream and stays at the current moment. */
    private void applyQuality(int height) {
        if (pageUrl == null || pageUrl.isEmpty()) {
            status.setText("No source is stored for this video");
            return;
        }
        if (Chute.videoId(pageUrl) != null) {
            status.setText("This video has one quality");
            return;
        }
        if (Bili.bvid(pageUrl) != null) {
            qualityHeight = height;
            status.setText("Quality " + qualityLabel(height));
            playBili(pageUrl, (int) Math.max(player.getCurrentPosition(), 0));
            return;
        }
        if (Nico.videoId(pageUrl) != null) {
            qualityHeight = height;
            status.setText("Quality " + qualityLabel(height));
            playNico(pageUrl, (int) Math.max(player.getCurrentPosition(), 0));
            return;
        }
        if (Kick.slug(pageUrl) != null || Odysee.videoUri(pageUrl) != null
                || Fosdem.key(pageUrl) != null) {
            status.setText("This stream has one quality");
            return;
        }
        if (RedBull.id(pageUrl) != null || Nasa.slug(pageUrl) != null) {
            status.setText("This stream has one quality");
            return;
        }
        if (Ted.slug(pageUrl) != null) {
            qualityHeight = height;
            status.setText("Quality " + qualityLabel(height));
            openTed(pageUrl, (int) Math.max(player.getCurrentPosition(), 0));
            return;
        }
        if (Archive.identifier(pageUrl) != null) {
            qualityHeight = height;
            status.setText("Quality " + qualityLabel(height));
            openArchive(pageUrl, (int) Math.max(player.getCurrentPosition(), 0));
            return;
        }
        if (Rumble.isWatch(pageUrl) || Rumble.embedId(pageUrl) != null) {
            qualityHeight = height;
            status.setText("Quality " + qualityLabel(height));
            openRumbleAt(pageUrl, (int) Math.max(player.getCurrentPosition(), 0));
            return;
        }
        qualityHeight = height;
        int at = (int) Math.max(player.getCurrentPosition(), 0);
        String page = pageUrl;
        status.setText("Quality " + qualityLabel(height));
        io.execute(() -> {
            try {
                StreamInfo info = StreamInfo.getInfo(page);
                runOnUiThread(() -> playChosen(info, at));
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> status.setText(message));
            }
        });
    }

    private void playChosen(StreamInfo info, int position) {
        if (qualityHeight == 0) {
            String audio = audioUrl(info);
            if (audio.isEmpty()) {
                status.setText("No audio stream");
                return;
            }
            play(audio, position);
            return;
        }
        int target = qualityHeight < 0 ? 720 : qualityHeight;
        VideoStream progressive = nearest(info.getVideoStreams(), target);
        if (progressive != null) {
            play(progressive.getContent(), position);
            return;
        }
        VideoStream picture = nearest(info.getVideoOnlyStreams(), target);
        String audio = audioUrl(info);
        if (picture != null && !audio.isEmpty()) {
            playPair(picture.getContent(), audio, position);
            return;
        }
        if (picture != null) {
            play(picture.getContent(), position);
            return;
        }
        String fallback = playUrl(info);
        if (fallback.isEmpty()) {
            status.setText("This video has one quality");
            return;
        }
        play(fallback, position);
    }

    private void playPair(String videoUrl, String audioUrl, int position) {
        playUrl = videoUrl;
        DefaultMediaSourceFactory factory = new DefaultMediaSourceFactory(PlayService.sources());
        MediaSource picture = factory.createMediaSource(new MediaItem.Builder()
                .setUri(videoUrl)
                .setMediaMetadata(new androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(title.getText().toString())
                        .build())
                .build());
        MediaSource sound = factory.createMediaSource(MediaItem.fromUri(audioUrl));
        player.setMediaSource(new MergingMediaSource(picture, sound));
        player.prepare();
        player.seekTo(position);
        player.setPlaybackSpeed(speed);
        player.play();
    }

    private static VideoStream nearest(List<VideoStream> streams, int targetHeight) {
        if (streams == null) {
            return null;
        }
        VideoStream best = null;
        int bestGap = Integer.MAX_VALUE;
        for (VideoStream stream : streams) {
            if (!stream.isUrl() || stream.getContent() == null || stream.getContent().isEmpty()) {
                continue;
            }
            if (stream.getContent().contains(".m3u8")) {
                continue;
            }
            int height = stream.getHeight();
            if (height <= 0) {
                continue;
            }
            int gap = Math.abs(height - targetHeight);
            if (gap < bestGap) {
                best = stream;
                bestGap = gap;
            }
        }
        return best;
    }

    private static String qualityLabel(int height) {
        if (height == 0) {
            return "Audio";
        }
        if (height < 0) {
            return "Auto";
        }
        return height + "p";
    }

    private void pickOffset() {
        int[] shifts = {-2000, -1000, -500, 0, 500, 1000, 2000};
        String[] labels = new String[shifts.length];
        for (int i = 0; i < shifts.length; i++) {
            labels[i] = offsetLabel(shifts[i]);
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Subtitle offset")
                .setItems(labels, (dialog, which) -> {
                    offsetMs = shifts[which];
                    followed = -1;
                    followTranscript();
                    paintOnScreen();
                    status.setText("Subtitle offset " + offsetLabel(offsetMs));
                })
                .show();
    }

    private static String speedLabel(float rate) {
        if (rate == 1f) {
            return "1×";
        }
        if (rate == 0.5f) {
            return "0.5×";
        }
        if (rate == 0.75f) {
            return "0.75×";
        }
        if (rate == 1.25f) {
            return "1.25×";
        }
        if (rate == 1.5f) {
            return "1.5×";
        }
        return "2×";
    }

    private static String offsetLabel(int ms) {
        if (ms == 0) {
            return "on time";
        }
        String seconds = ms % 1000 == 0
                ? Integer.toString(Math.abs(ms) / 1000)
                : String.format(Locale.US, "%.1f", Math.abs(ms) / 1000f);
        return ms < 0 ? seconds + " s earlier" : seconds + " s later";
    }

    private void saveExcerpt(long trackId, int t0, int byteOffset, String text) {
        Shelf.Video video = Shelf.video(db, videoId);
        if (video == null || text == null || text.isEmpty()) {
            status.setText("Nothing to save");
            return;
        }
        Shelf.saveExcerpt(db, video.id, trackId, t0, byteOffset, text, video.title, video.channel);
        status.setText("Saved excerpt");
    }

    private void shareExcerpt(String text, int t0) {
        android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(android.content.Intent.EXTRA_TEXT,
                text + "\n— " + title.getText() + " " + stamp(t0));
        startActivity(android.content.Intent.createChooser(send, getString(R.string.excerpts)));
    }

    private void showExcerpts() {
        excerptMode = true;
        showTab(1);
        reloadLibrary();
    }

    private void pickSubtitles() {
        if (tracks.isEmpty()) {
            status.setText("No captions");
            return;
        }
        String[] labels = new String[tracks.size()];
        boolean[] checked = new boolean[tracks.size()];
        for (int i = 0; i < tracks.size(); i++) {
            labels[i] = tracks.get(i).label;
            checked[i] = trackShown(i);
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.subtitles)
                .setMultiChoiceItems(labels, checked, (dialog, which, on) -> {
                    int count = 0;
                    for (boolean pick : checked) {
                        if (pick) {
                            count++;
                        }
                    }
                    if (count > SLOTS) {
                        checked[which] = false;
                        ((androidx.appcompat.app.AlertDialog) dialog).getListView()
                                .setItemChecked(which, false);
                        status.setText("Four languages at a time");
                    }
                })
                .setPositiveButton("Show", (dialog, which) -> applySubtitleChecks(checked))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean trackShown(int trackIndex) {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (chosen(slot) == trackIndex) {
                return true;
            }
        }
        return false;
    }

    private void applySubtitleChecks(boolean[] checked) {
        int[] pick = {-1, -1, -1, -1};
        int n = 0;
        int current = chosen(0);
        if (current >= 0 && current < checked.length && checked[current]) {
            pick[n++] = current;
        }
        for (int i = 0; i < checked.length && n < SLOTS; i++) {
            if (checked[i] && i != pick[0]) {
                pick[n++] = i;
            }
        }
        if (pick[0] < 0) {
            pick[0] = tracks.isEmpty() ? 0 : defaultPrimary(tracks);
        }
        filling = true;
        for (int i = 0; i < SLOTS; i++) {
            int sel = i == 0 ? Math.max(pick[i], 0) : pick[i] + 1;
            if (sel < 0) {
                sel = 0;
            }
            slots[i].setSelection(sel);
        }
        filling = false;
        render();
    }

    private void editLine(int position) {
        if (position < 0 || position >= spans.size() || chosen(0) < 0) {
            return;
        }
        Cues.Span span = spans.get(position);
        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(span.text);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(stamp(span.t0))
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    long trackId = tracks.get(chosen(0)).id;
                    Shelf.editSpan(db, db, trackId, position, input.getText().toString());
                    show(Shelf.video(db, videoId));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openChannel() {
        String url = Shelf.channelUrl(db, videoId);
        if (url.isEmpty()) {
            status.setText("No channel URL for this video");
            return;
        }
        status.setText("Opening channel");
        io.execute(() -> {
            try {
                org.schabi.newpipe.extractor.StreamingService service = NewPipe.getServiceByUrl(url);
                Browse.Session session = Browse.channel(service, url);
                runOnUiThread(() -> showSession(session, service.getServiceInfo().getName()));
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> status.setText(message));
            }
        });
    }

    /** The HTTP code and host when the CDN refused the stream. */
    private static String playFailure(androidx.media3.common.PlaybackException error) {
        Throwable cause = error.getCause();
        while (cause != null) {
            if (cause instanceof androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException http) {
                String host = "";
                if (http.dataSpec != null && http.dataSpec.uri != null
                        && http.dataSpec.uri.getHost() != null) {
                    host = " " + http.dataSpec.uri.getHost();
                }
                return "HTTP " + http.responseCode + host;
            }
            cause = cause.getCause();
        }
        cause = error.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isEmpty()) {
            String message = cause.getMessage();
            if (message.length() > 120) {
                message = message.substring(0, 120);
            }
            return cause.getClass().getSimpleName() + ": " + message;
        }
        return "This source is not available";
    }

    private void jump(int t0) {
        showVideo();
        String local = Shelf.prefer(Shelf.localPath(db, videoId), "");
        if (!local.isEmpty()) {
            play(local, t0);
            return;
        }
        if (pageUrl == null || pageUrl.isEmpty()) {
            status.setText("No source is stored for this video");
            return;
        }
        status.setText("Opening");
        String page = pageUrl;
        long id = videoId;
        if (Chute.videoId(page) != null) {
            String chute = Chute.videoId(page);
            io.execute(() -> {
                try {
                    String media = Chute.media(postJson(Chute.MEDIA, Chute.idBody(chute)));
                    if (media.isEmpty()) {
                        throw new java.io.IOException("no stream");
                    }
                    Shelf.setPlayUrl(db, id, media);
                    runOnUiThread(() -> play(media, t0));
                } catch (Exception e) {
                    runOnUiThread(() -> status.setText("This video is gone"));
                }
            });
            return;
        }
        if (Bili.bvid(page) != null) {
            playBili(page, t0);
            return;
        }
        if (Nico.videoId(page) != null) {
            playNico(page, t0);
            return;
        }
        if ((Rumble.isWatch(page) || Rumble.embedId(page) != null)
                && playUrl != null && playUrl.contains("rumble.cloud")) {
            play(playUrl, t0);
            return;
        }
        if (Kick.slug(page) != null) {
            openKick(page);
            return;
        }
        if (Odysee.videoUri(page) != null && playUrl != null && playUrl.contains("odycdn.com")) {
            play(playUrl, t0);
            return;
        }
        if (Fosdem.key(page) != null && playUrl != null && playUrl.contains(".mp4")) {
            play(playUrl, t0);
            return;
        }
        if (Archive.identifier(page) != null && playUrl != null && playUrl.contains("archive.org/download/")) {
            play(playUrl, t0);
            return;
        }
        if (Ted.slug(page) != null && playUrl != null
                && (playUrl.contains("download.ted.com") || playUrl.contains("hls.ted.com"))) {
            play(playUrl, t0);
            return;
        }
        if (Nasa.slug(page) != null && playUrl != null && playUrl.contains(".m3u8")) {
            play(playUrl, t0);
            return;
        }
        if (RedBull.id(page) != null && playUrl != null && playUrl.contains("play.redbull.com")) {
            play(playUrl, t0);
            return;
        }
        io.execute(() -> {
            try {
                StreamInfo info = StreamInfo.getInfo(page);
                String fresh = playUrl(info);
                if (fresh.isEmpty()) {
                    throw new java.io.IOException("no stream");
                }
                Shelf.setPlayUrl(db, id, fresh);
                runOnUiThread(() -> play(fresh, t0));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("This video is gone"));
            }
        });
    }

    /** Writes the playhead when it has moved, and clears it near the end. */
    private void rememberPlace() {
        if (videoId <= 0 || player.getMediaItemCount() == 0) {
            return;
        }
        int duration = 0;
        long length = player.getDuration();
        if (length > 0 && length < Integer.MAX_VALUE) {
            duration = (int) length;
        }
        int keep = Shelf.keptPlace((int) player.getCurrentPosition(), duration);
        if (keep < 0) {
            return;
        }
        if (remembered >= 0 && keep > 0 && Math.abs(keep - remembered) < 5000) {
            return;
        }
        remembered = keep;
        Shelf.remember(db, videoId, keep);
    }

    @Override
    protected void onPause() {
        rememberPlace();
        super.onPause();
    }

    private void play(String url, int t0) {
        playUrl = url;
        android.net.Uri uri = url.startsWith("/")
                ? android.net.Uri.fromFile(new java.io.File(url))
                : android.net.Uri.parse(url);
        player.setMediaItem(new MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(new androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(title.getText().toString())
                        .build())
                .build());
        player.prepare();
        player.seekTo(t0);
        player.play();
        fitAudio(audioFile(url));
    }

    /** An audio file does not need the tall black picture. */
    private static boolean audioFile(String url) {
        if (url == null) {
            return false;
        }
        String path = url.toLowerCase(Locale.US);
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        return path.endsWith(".mp3") || path.endsWith(".m4a") || path.endsWith(".m4b")
                || path.endsWith(".aac") || path.endsWith(".ogg") || path.endsWith(".opus")
                || path.endsWith(".flac") || path.endsWith(".wav");
    }

    private void fitAudio(boolean audio) {
        if (mini) {
            return;
        }
        android.widget.FrameLayout frame = findViewById(R.id.player_frame);
        android.widget.LinearLayout.LayoutParams lp =
                (android.widget.LinearLayout.LayoutParams) frame.getLayoutParams();
        lp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = dp(audio ? 56 : 200);
        frame.setLayoutParams(lp);
        PlayerView playerView = findViewById(R.id.player);
        playerView.setControllerShowTimeoutMs(audio ? 0 : 5000);
        if (audio) {
            playerView.showController();
        }
    }

    private void keep() {
        if (pageUrl == null || pageUrl.isEmpty()) {
            status.setText("Nothing to keep");
            return;
        }
        status.setText("Saving");
        String page = pageUrl;
        long id = videoId;
        try {
            NewPipe.getServiceByUrl(page);
        } catch (Exception notAService) {
            if (playUrl != null && playUrl.contains(".m3u8")) {
                status.setText("This stream is a playlist");
                return;
            }
            keepDirect(playUrl, id);
            return;
        }
        io.execute(() -> {
            try {
                StreamInfo info = StreamInfo.getInfo(page);
                String source = fileUrl(info, qualityHeight);
                if (source.isEmpty()) {
                    throw new java.io.IOException("no stream");
                }
                java.io.File dir = getExternalFilesDir("media");
                if (dir == null) {
                    dir = getFilesDir();
                }
                String ext = qualityHeight == 0 || source.contains(".m4a") || source.contains("mime=audio")
                        ? ".m4a" : ".mp4";
                java.io.File dest = new java.io.File(dir, id + ext);
                // A default OkHttp client stops after 10s of silence. The first
                // bytes of a YouTube stream took about 8s from this network.
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                        .build();
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(source)
                        .header("User-Agent", OkDownloader.USER_AGENT)
                        .build();
                try (okhttp3.Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        throw new java.io.IOException("HTTP " + response.code());
                    }
                    try (java.io.InputStream in = response.body().byteStream();
                         java.io.OutputStream out = new java.io.FileOutputStream(dest)) {
                        byte[] buf = new byte[8192];
                        int n;
                        long got = 0;
                        long reported = -1;
                        while ((n = in.read(buf)) >= 0) {
                            out.write(buf, 0, n);
                            got += n;
                            long mb = got / (1024 * 1024);
                            if (mb != reported) {
                                reported = mb;
                                runOnUiThread(() -> status.setText("Saving " + mb + " MB"));
                            }
                        }
                    }
                } catch (Exception e) {
                    dest.delete();
                    throw e;
                }
                Shelf.setLocalPath(db, id, dest.getAbsolutePath());
                runOnUiThread(() -> {
                    status.setText("Kept " + dest.getName());
                    reloadDownloads();
                });
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> status.setText(message));
            }
        });
    }

    private void openLibraryRow(int position) {
        if (libraryHits) {
            if (position < 0 || position >= hits.size()) {
                return;
            }
            Shelf.Hit hit = hits.get(position);
            show(Shelf.video(db, hit.videoId), false);
            showVideo();
            int index = indexOfHit(hit);
            int at = 0;
            if (index >= 0 && index < spans.size()) {
                followLines = true;
                followed = index;
                lines.setItemChecked(index, true);
                lines.setSelectionFromTop(index, 0);
                at = spans.get(index).t0;
            }
            jump(at);
            return;
        }
        if (position < 0 || position >= shelfLines.size()) {
            return;
        }
        ShelfLine line = shelfLines.get(position);
        if (line.video == null) {
            if (line.channel == null || line.channel.isEmpty()) {
                return;
            }
            shelfChannel = line.channel.equals(shelfChannel) ? "" : line.channel;
            reloadLibrary();
            return;
        }
        int at = Shelf.place(db, line.video.id);
        show(line.video, false);
        showVideo();
        jump(at);
    }

    private int indexOfHit(Shelf.Hit hit) {
        List<Cues.Span> row = Shelf.readSpans(db, hit.trackId);
        int at = Cues.spanIndex(row, hit.byteOffset);
        if (at < 0) {
            return -1;
        }
        if (chosen(0) >= 0 && tracks.get(chosen(0)).id == hit.trackId) {
            return at;
        }
        Cues.Span span = row.get(at);
        return Cues.overlapping(spans, span.t0, span.t1);
    }

    private void fillServices() {
        int previous = filterService.getSelectedItemPosition();
        String previousName = previous > 0 && filterService.getSelectedItem() != null
                ? filterService.getSelectedItem().toString() : "";
        List<String> names = new ArrayList<>();
        names.add("All");
        names.addAll(Shelf.services(db));
        filling = true;
        filterService.setAdapter(spinner(names));
        int sel = 0;
        int at = names.indexOf(previousName);
        if (at > 0) {
            sel = at;
        }
        filterService.setSelection(sel);
        filling = false;
    }

    private void reloadLibrary() {
        int sort = Math.max(sortBy.getSelectedItemPosition(), 0);
        String service = "";
        if (filterService.getSelectedItemPosition() > 0 && filterService.getSelectedItem() != null) {
            service = filterService.getSelectedItem().toString();
        }
        String query = libraryQuery.getText().toString().trim();
        if (excerptMode) {
            List<Shelf.Hit> all = Shelf.excerpts(db);
            if (!query.isEmpty()) {
                String needle = query.toLowerCase(Locale.ROOT);
                List<Shelf.Hit> kept = new ArrayList<>();
                for (Shelf.Hit hit : all) {
                    if (hit.quote.toLowerCase(Locale.ROOT).contains(needle)
                            || hit.title.toLowerCase(Locale.ROOT).contains(needle)
                            || hit.channel.toLowerCase(Locale.ROOT).contains(needle)) {
                        kept.add(hit);
                    }
                }
                all = kept;
            }
            hits = all;
            libraryHits = true;
            hitConcepts = false;
            libraryStatus.setText(hits.size() + " excerpts");
            library.setAdapter(new LibraryAdapter());
            return;
        }
        if (!query.isEmpty()) {
            boolean fuzzy = ((android.widget.CheckBox) findViewById(R.id.fuzzy)).isChecked();
            boolean concepts = ((android.widget.CheckBox) findViewById(R.id.concepts)).isChecked();
            boolean meaning = ((android.widget.CheckBox) findViewById(R.id.meaning)).isChecked();
            hitConcepts = concepts;
            if (meaning) {
                searchMeaning(query, fuzzy, concepts, service);
                return;
            }
            try {
                hits = Shelf.searchAll(db, db, Query.match(query, fuzzy, concepts), service);
                libraryHits = true;
                libraryStatus.setText(hits.size() + " hits");
            } catch (RuntimeException e) {
                hits = Collections.emptyList();
                libraryHits = true;
                libraryStatus.setText(e.getMessage());
            }
        } else {
            libraryHits = false;
            shelfVideos = Shelf.gathered(Shelf.list(db, sort, service));
            if (!shelfChannel.isEmpty()) {
                List<Shelf.Video> narrowed = Shelf.onlyChannel(shelfVideos, shelfChannel);
                if (narrowed.isEmpty()) {
                    shelfChannel = "";
                } else {
                    shelfVideos = narrowed;
                }
            }
            shelfLines = shelfLines(shelfVideos);
            String label = shelfVideos.size() + " videos";
            if (!shelfChannel.isEmpty()) {
                label = shelfChannel + " · " + label;
            }
            libraryStatus.setText(label);
        }
        library.setAdapter(new LibraryAdapter());
    }

    /** A channel heading, then that channel's videos, in shelf order. */
    private static List<ShelfLine> shelfLines(List<Shelf.Video> videos) {
        List<ShelfLine> lines = new ArrayList<>();
        String previous = null;
        for (Shelf.Video video : videos) {
            String name = video.channel == null ? "" : video.channel;
            if (!name.equals(previous)) {
                lines.add(new ShelfLine(name));
                previous = name;
            }
            lines.add(new ShelfLine(video));
        }
        return lines;
    }

    /** Word hits plus paragraph vectors. The model is fetched the first time this runs. */
    private void searchMeaning(String query, boolean fuzzy, boolean concepts, String service) {
        hits = Collections.emptyList();
        libraryHits = true;
        library.setAdapter(new LibraryAdapter());
        libraryStatus.setText("Meaning");
        io.execute(() -> {
            try {
                List<Shelf.Hit> lexical = Collections.emptyList();
                try {
                    lexical = Shelf.searchAll(db, db, Query.match(query, fuzzy, concepts), service);
                } catch (RuntimeException ignored) {
                    // A vector can still rank a line when the word query does not parse.
                }
                float[] asked = Meaning.query(this, query, pct -> runOnUiThread(() ->
                        libraryStatus.setText(pct < 0 ? "Meaning" : "Model " + pct + "%")));
                List<String[]> tracks = db.rows("SELECT id, video_id FROM track");
                List<Near.Scored> scored = new ArrayList<>();
                int done = 0;
                for (String[] row : tracks) {
                    long trackId = Long.parseLong(row[0]);
                    long videoId = Long.parseLong(row[1]);
                    Shelf.Video video = Shelf.video(db, videoId);
                    if (video == null) {
                        continue;
                    }
                    if (service != null && !service.isEmpty() && !service.equals(video.service)) {
                        continue;
                    }
                    List<Cues.Span> rowSpans = Shelf.readSpans(db, trackId);
                    for (Near.Paragraph paragraph : Near.paragraphs(rowSpans)) {
                        int hash = paragraph.text.hashCode() ^ Meaning.SPACE;
                        float[] vector = db.readVec(trackId, paragraph.spanIndex, hash);
                        if (vector == null) {
                            vector = Meaning.passage(this, paragraph.text);
                            db.putVec(trackId, paragraph.spanIndex, hash, vector);
                        }
                        String quote = paragraph.text.length() > 180
                                ? paragraph.text.substring(0, 180) : paragraph.text;
                        int t0 = paragraph.spanIndex >= 0 && paragraph.spanIndex < rowSpans.size()
                                ? rowSpans.get(paragraph.spanIndex).t0 : -1;
                        scored.add(new Near.Scored(new Shelf.Hit(
                                video.id, trackId, paragraph.byteOffset,
                                video.title, video.channel, quote, t0), Near.cosine(asked, vector)));
                    }
                    done++;
                    int shown = done;
                    int total = tracks.size();
                    runOnUiThread(() -> libraryStatus.setText("Meaning " + shown + "/" + total));
                }
                List<Shelf.Hit> found = Near.blend(lexical, scored);
                runOnUiThread(() -> {
                    hits = found;
                    libraryHits = true;
                    libraryStatus.setText(found.size() + " hits");
                    library.setAdapter(new LibraryAdapter());
                });
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> libraryStatus.setText(message));
            }
        });
    }

    private void fetch(String page) {
        if (page.isEmpty()) {
            status.setText("Paste a video URL");
            return;
        }
        status.setText("Fetching");
        io.execute(() -> {
            try {
                Held held = capture(page, true);
                runOnUiThread(() -> {
                    show(held.video);
                    if (!held.captions) {
                        status.setText(held.note);
                    }
                });
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> status.setText(message));
            }
        });
    }

    /** Saves one video. Captions are optional, so a later Check does not list it again. */
    private Held capture(String page, boolean report) throws Exception {
        StreamingService service = NewPipe.getServiceByUrl(page);
        StreamInfo info = StreamInfo.getInfo(service, page);
        List<SubtitlesStream> subs = info.getSubtitles();
        List<Shelf.TrackIn> incoming = new ArrayList<>();
        int total = subs == null ? 0 : subs.size();
        String lastError = null;
        for (int i = 0; i < total; i++) {
            SubtitlesStream track = subs.get(i);
            if (report) {
                int at = i + 1;
                runOnUiThread(() -> status.setText("Caption " + at + " of " + total));
            }
            try {
                String raw = track.isUrl()
                        ? NewPipe.getDownloader().get(track.getContent()).responseBody()
                        : track.getContent();
                MediaFormat format = track.getFormat();
                Cues.Transcript transcript = Cues.parse(
                        raw, format == null ? null : format.suffix, track.isAutoGenerated());
                if (transcript.spans.isEmpty()) {
                    continue;
                }
                String lang = track.getLanguageTag() == null ? "" : track.getLanguageTag();
                String kind = track.isAutoGenerated() ? "auto" : "manual";
                String label = lang + " · " + kind;
                if (track.getName() != null && !track.getName().isEmpty()) {
                    label = track.getName() + " · " + label;
                }
                incoming.add(new Shelf.TrackIn(lang, transcript.source, label, transcript));
            } catch (Exception e) {
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }
        String channelKey = channelKey(info.getUploaderUrl(), info.getUploaderName());
        long id = Shelf.save(
                db,
                service.getServiceInfo().getName(),
                channelKey,
                blank(info.getUploaderName(), "Unknown"),
                info.getUploaderUrl() == null ? "" : info.getUploaderUrl(),
                info.getId(),
                blank(info.getName(), page),
                page,
                playUrl(info),
                incoming);
        Shelf.setThumb(db, id, Browse.thumbnailOf(info.getThumbnails()));
        String note = lastError == null ? "No captions" : "No captions (" + lastError + ")";
        return new Held(Shelf.video(db, id), !incoming.isEmpty(), note);
    }

    private static final class Held {
        final Shelf.Video video;
        final boolean captions;
        final String note;

        Held(Shelf.Video video, boolean captions, String note) {
            this.video = video;
            this.captions = captions;
            this.note = note;
        }
    }

    private void translate() {
        if (tracks.isEmpty()) {
            status.setText("No transcript to translate");
            return;
        }
        int primary = chosen(0);
        if (primary < 0) {
            primary = 0;
        }
        String tag = tracks.get(primary).lang;
        String source = TranslateLanguage.fromLanguageTag(tag);
        if (source == null && tag != null && tag.length() >= 2) {
            source = TranslateLanguage.fromLanguageTag(tag.substring(0, 2));
        }
        if (source == null) {
            status.setText("No translation model for " + tag);
            return;
        }
        String[] labels = {
                "English", "Spanish", "German", "French", "Portuguese",
                "Chinese", "Japanese", "Korean", "Russian"
        };
        String[] codes = {
                TranslateLanguage.ENGLISH, TranslateLanguage.SPANISH, TranslateLanguage.GERMAN,
                TranslateLanguage.FRENCH, TranslateLanguage.PORTUGUESE, TranslateLanguage.CHINESE,
                TranslateLanguage.JAPANESE, TranslateLanguage.KOREAN, TranslateLanguage.RUSSIAN
        };
        String from = source;
        long trackId = tracks.get(primary).id;
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.translate)
                .setItems(labels, (dialog, which) -> translateInto(trackId, from, codes[which]))
                .show();
    }

    private void translateInto(long trackId, String source, String target) {
        if (source.equals(target)) {
            status.setText("Already that language");
            return;
        }
        status.setText("Translation model");
        List<Cues.Span> sourceSpans = Shelf.readSpans(db, trackId);
        long id = videoId;
        io.execute(() -> {
            Translator translator = null;
            try {
                translator = Translation.getClient(new TranslatorOptions.Builder()
                        .setSourceLanguage(source)
                        .setTargetLanguage(target)
                        .build());
                Tasks.await(translator.downloadModelIfNeeded());
                List<String> lines = new ArrayList<>();
                for (int i = 0; i < sourceSpans.size(); i++) {
                    int at = i + 1;
                    runOnUiThread(() -> status.setText("Translate " + at + " of " + sourceSpans.size()));
                    lines.add(Tasks.await(translator.translate(sourceSpans.get(i).text)));
                }
                Cues.Transcript transcript = Cues.withLines(sourceSpans, lines, "translation");
                Shelf.addTrack(db, id, new Shelf.TrackIn(
                        target, "translation", target + " · translation", transcript));
                Shelf.Video video = Shelf.video(db, id);
                runOnUiThread(() -> show(video));
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> status.setText(message));
            } finally {
                if (translator != null) {
                    translator.close();
                }
            }
        });
    }

    private void show(Shelf.Video video) {
        show(video, true);
    }

    private void show(Shelf.Video video, boolean prepare) {
        videoId = video.id;
        remembered = -1;
        followLines = true;
        followed = -1;
        playUrl = video.playUrl == null ? "" : video.playUrl;
        pageUrl = video.pageUrl == null ? "" : video.pageUrl;
        triedFresh = false;
        offsetMs = 0;
        tracks = Shelf.readTracks(db, video.id);
        String heading = video.channel + " — " + video.title;
        title.setText(heading);
        status.setText("");
        ((Button) findViewById(R.id.channel)).setText(video.channel);
        paintFollow();
        relatedMode = false;
        related = Collections.emptyList();
        description.setText("");
        loadDetail(video.id, pageUrl);
        fillServices();
        reloadLibrary();
        filling = true;
        List<String> names = labelsOf(tracks);
        List<String> optional = new ArrayList<>();
        optional.add(getString(R.string.no_track));
        optional.addAll(names);
        int[] pick = defaultSlots(tracks);
        for (int i = 0; i < SLOTS; i++) {
            slots[i].setAdapter(spinner(i == 0 ? names : optional));
            int sel = i == 0 ? pick[0] : pick[i] + 1;
            slots[i].setSelection(Math.max(sel, 0));
        }
        filling = false;
        render();
        player.setPlaybackSpeed(speed);
        if (prepare && Bili.bvid(pageUrl) != null) {
            playBili(pageUrl, 0);
        } else if (prepare && Nico.videoId(pageUrl) != null) {
            playNico(pageUrl, 0);
        } else if (prepare && Kick.slug(pageUrl) != null) {
            openKick(pageUrl);
        } else if (prepare && video.playUrl != null && !video.playUrl.isEmpty()) {
            player.setMediaItem(new MediaItem.Builder()
                    .setUri(video.playUrl)
                    .setMediaMetadata(new androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(video.title)
                            .setArtist(video.channel)
                            .build())
                    .build());
            player.prepare();
        } else if (!prepare) {
            player.clearMediaItems();
        }
    }

    private void loadDetail(long id, String page) {
        if (page == null || page.isEmpty()) {
            return;
        }
        io.execute(() -> {
            try {
                StreamInfo info = StreamInfo.getInfo(page);
                List<Browse.Found> items = Browse.fromItems(info.getRelatedItems());
                String text = "";
                if (info.getDescription() != null && info.getDescription().content() != null) {
                    text = info.getDescription().content()
                            .replaceAll("<[^>]+>", " ")
                            .replaceAll("\\s+", " ")
                            .trim();
                }
                if (text.length() > 320) {
                    text = text.substring(0, 320) + "…";
                }
                String shown = text;
                Shelf.setThumb(db, id, Browse.thumbnailOf(info.getThumbnails()));
                runOnUiThread(() -> {
                    if (!page.equals(pageUrl)) {
                        return;
                    }
                    related = items;
                    description.setText(shown);
                    if (relatedMode) {
                        render();
                    }
                });
            } catch (Exception ignored) {
                // The player and transcript still work without the blurb.
            }
        });
    }

    private void openRelated(int position) {
        if (position < 0 || position >= related.size()) {
            return;
        }
        Browse.Found item = related.get(position);
        if ("video".equals(item.kind)) {
            openVideo(item.url);
        }
    }

    private void render() {
        if (lineSearch != null) {
            lineSearch.setVisibility(relatedMode ? View.GONE : View.VISIBLE);
        }
        if (relatedMode) {
            lines.setAdapter(new RelatedAdapter());
            return;
        }
        if (tracks.isEmpty()) {
            spans = Collections.emptyList();
            extras = Collections.emptyList();
            lines.setAdapter(null);
            paintOnScreen();
            return;
        }
        int p = chosen(0);
        if (p < 0) {
            p = 0;
        }
        spans = Shelf.readSpans(db, tracks.get(p).id);
        List<List<Cues.Span>> extras = new ArrayList<>();
        boolean[] used = new boolean[tracks.size()];
        used[p] = true;
        for (int i = 1; i < SLOTS; i++) {
            int s = chosen(i);
            if (s < 0 || used[s]) {
                continue;
            }
            used[s] = true;
            extras.add(Shelf.readSpans(db, tracks.get(s).id));
        }
        this.extras = extras;
        blocks = prose ? Prose.blocks(spans) : Collections.emptyList();
        followed = -1;
        markStart = -1;
        markEnd = -1;
        lines.setAdapter(new LineAdapter(spans, extras, blocks));
        followTranscript();
        paintOnScreen();
    }

    /** A thin position line that stays after the player controls fade. */
    private void paintLine() {
        android.widget.ProgressBar line = findViewById(R.id.play_line);
        if (line == null || player == null) {
            return;
        }
        long duration = player.getDuration();
        if (duration <= 0 || duration == androidx.media3.common.C.TIME_UNSET) {
            line.setProgress(0);
            return;
        }
        line.setProgress((int) (player.getCurrentPosition() * 1000 / duration));
    }

    private void paintOnScreen() {
        if (burn == null || onScreen == null || !onScreen.isChecked()) {
            if (burn != null) {
                burn.setVisibility(View.GONE);
            }
            return;
        }
        int time = cueClock();
        String text = Cues.captionAt(spans, extras, time);
        if (text.isEmpty()) {
            burn.setVisibility(View.GONE);
            return;
        }
        int[] range = {-1, -1};
        int at = Cues.indexAt(spans, time);
        if (at >= 0) {
            Cues.Span span = spans.get(at);
            if (time >= span.t0 && time < span.t1 && span.text != null && text.startsWith(span.text)) {
                int[] word = Prose.wordAt(span.text, span.t0, span.t1, time);
                range = Prose.phraseAround(span.text, word[0], word[1]);
            }
        }
        setUnderlined(burn, text, range[0], range[1]);
        burn.setVisibility(View.VISIBLE);
    }

    /** Marks the span under the playhead and keeps it on screen while follow is on. */
    private void followTranscript() {
        if (!videoOpen || relatedMode || spans.isEmpty() || lines.getAdapter() == null) {
            return;
        }
        int spanAt = Cues.indexAt(spans, cueClock());
        int at = prose ? Prose.blockAt(blocks, spanAt) : spanAt;
        if (at < 0) {
            return;
        }
        int[] word = wordRange(at);
        boolean moved = at != followed;
        boolean wordMoved = moved || word[0] != markStart || word[1] != markEnd;
        int previous = followed;
        followed = at;
        markStart = word[0];
        markEnd = word[1];
        if (moved) {
            lines.setItemChecked(at, true);
            if (previous >= 0) {
                paintRow(previous);
            }
        }
        if (wordMoved) {
            paintRow(at);
        }
        if (!followLines || !moved) {
            return;
        }
        int first = lines.getFirstVisiblePosition();
        int last = lines.getLastVisiblePosition();
        if (first < 0 || at < first || at >= last) {
            lines.setSelectionFromTop(at, Math.max(lines.getHeight() / 5, 0));
        }
    }

    /** Range of the current word in the row's text. A line includes the stamp. */
    private int[] wordRange(int at) {
        int time = cueClock();
        if (prose) {
            return Prose.wordAt(spans, blocks, at, time);
        }
        Cues.Span span = spans.get(at);
        int[] range = Prose.wordAt(span.text == null ? "" : span.text, span.t0, span.t1, time);
        if (range[0] < 0) {
            return range;
        }
        int pad = (stamp(span.t0) + "  ").length();
        return new int[] {range[0] + pad, range[1] + pad};
    }

    private void paintRow(int position) {
        int child = position - lines.getFirstVisiblePosition();
        if (child < 0 || child >= lines.getChildCount()) {
            return;
        }
        View row = lines.getChildAt(child);
        android.widget.ListAdapter adapter = lines.getAdapter();
        if (row != null && adapter != null) {
            adapter.getView(position, row, lines);
        }
    }

    private void setUnderlined(TextView view, String text, int start, int end) {
        if (text == null || start < 0 || end <= start || end > text.length()) {
            view.setText(text);
            return;
        }
        android.text.SpannableString marked = new android.text.SpannableString(text);
        marked.setSpan(new android.text.style.UnderlineSpan(), start, end,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(marked);
    }



    private void setMarks(TextView view, String text, java.util.List<Query.Mark> marks) {
        if (text == null || marks == null || marks.isEmpty()) {
            view.setText(text);
            return;
        }
        android.text.SpannableString marked = new android.text.SpannableString(text);
        int flags = android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        int accent = getColor(R.color.accent);
        for (Query.Mark mark : marks) {
            if (mark.start < 0 || mark.end > text.length() || mark.end <= mark.start) {
                continue;
            }
            int color = mark.related ? 0xFF64B5F6 : accent;
            marked.setSpan(new android.text.style.ForegroundColorSpan(color), mark.start, mark.end, flags);
            marked.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    mark.start, mark.end, flags);
        }
        view.setText(marked);
    }

    /** Track index for a slot. Slots after the first use position 0 as None. */
    private int chosen(int slot) {
        int pos = slots[slot].getSelectedItemPosition();
        if (slot > 0) {
            pos -= 1;
        }
        if (pos < 0 || pos >= tracks.size()) {
            return -1;
        }
        return pos;
    }

    private void find(String match) {
        if (videoId == 0 || match.isEmpty() || tracks.isEmpty()) {
            return;
        }
        try {
            int index = -1;
            for (int i = 0; i < SLOTS && index < 0; i++) {
                int s = chosen(i);
                if (s < 0) {
                    continue;
                }
                List<Cues.Span> row = i == 0 ? spans : Shelf.readSpans(db, tracks.get(s).id);
                int at = Cues.spanIndex(row, Shelf.searchByte(db, tracks.get(s).id, match));
                if (at < 0) {
                    continue;
                }
                if (i == 0) {
                    index = at;
                } else {
                    Cues.Span hit = row.get(at);
                    index = Cues.overlapping(spans, hit.t0, hit.t1);
                }
            }
            if (index < 0) {
                status.setText("No hit");
                return;
            }
            int show = prose ? Prose.blockAt(blocks, index) : index;
            if (show < 0) {
                show = 0;
            }
            lines.setItemChecked(show, true);
            lines.smoothScrollToPosition(show);
            playHere(spans.get(index).t0);
        } catch (RuntimeException e) {
            status.setText(e.getMessage());
        }
    }

    private void seek(int t0) {
        if (player.getPlaybackState() == ExoPlayer.STATE_IDLE) {
            player.prepare();
        }
        player.seekTo(t0);
        player.play();
    }

    private ArrayAdapter<String> spinner(List<String> labels) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private static List<String> labelsOf(List<Shelf.Track> tracks) {
        List<String> labels = new ArrayList<>();
        for (Shelf.Track track : tracks) {
            labels.add(track.label);
        }
        return labels;
    }

    /** Prefer a manual English track, then any manual track, else the first. */
    private static int defaultPrimary(List<Shelf.Track> tracks) {
        int manual = -1;
        for (int i = 0; i < tracks.size(); i++) {
            Shelf.Track track = tracks.get(i);
            boolean en = track.lang.toLowerCase(Locale.US).startsWith("en");
            if ("manual".equals(track.source) && en) {
                return i;
            }
            if (manual < 0 && "manual".equals(track.source)) {
                manual = i;
            }
        }
        return manual < 0 ? 0 : manual;
    }

    /**
     * Four slot indexes. The first prefers a manual English track. The rest fill
     * with other languages, in shelf order, and stay empty when no language is left.
     */
    static int[] defaultSlots(List<Shelf.Track> tracks) {
        int[] pick = {-1, -1, -1, -1};
        if (tracks.isEmpty()) {
            return pick;
        }
        pick[0] = defaultPrimary(tracks);
        boolean[] used = new boolean[tracks.size()];
        used[pick[0]] = true;
        String lang = tracks.get(pick[0]).lang;
        int n = 1;
        for (int i = 0; i < tracks.size() && n < SLOTS; i++) {
            if (used[i] || tracks.get(i).lang.equalsIgnoreCase(lang)) {
                continue;
            }
            pick[n++] = i;
            used[i] = true;
        }
        return pick;
    }

    private static String playUrl(StreamInfo info) {
        String hls = info.getHlsUrl();
        if (hls != null && !hls.isEmpty()) {
            return hls;
        }
        String video = firstUrl(info.getVideoStreams());
        if (!video.isEmpty()) {
            return video;
        }
        return firstAudio(info.getAudioStreams());
    }

    private static String audioUrl(StreamInfo info) {
        return firstAudio(info.getAudioStreams());
    }

    /**
     * One file for Keep. Audio quality is the audio stream. Otherwise the
     * progressive file nearest the target, then a picture-only file, then audio.
     * Auto uses 720. Playlists are skipped.
     */
    private static String fileUrl(StreamInfo info, int qualityHeight) {
        if (qualityHeight == 0) {
            return audioUrl(info);
        }
        int target = qualityHeight < 0 ? 720 : qualityHeight;
        VideoStream progressive = nearest(info.getVideoStreams(), target);
        if (progressive != null) {
            return progressive.getContent();
        }
        VideoStream picture = nearest(info.getVideoOnlyStreams(), target);
        if (picture != null) {
            return picture.getContent();
        }
        return audioUrl(info);
    }

    private static String firstUrl(List<VideoStream> streams) {
        if (streams == null) {
            return "";
        }
        for (VideoStream stream : streams) {
            if (stream.isUrl() && stream.getContent() != null && !stream.getContent().isEmpty()) {
                return stream.getContent();
            }
        }
        return "";
    }

    private static String firstAudio(List<AudioStream> streams) {
        if (streams == null) {
            return "";
        }
        for (AudioStream stream : streams) {
            if (stream.isUrl() && stream.getContent() != null && !stream.getContent().isEmpty()) {
                return stream.getContent();
            }
        }
        return "";
    }

    private static String channelKey(String uploaderUrl, String uploaderName) {
        if (uploaderUrl != null && !uploaderUrl.isEmpty()) {
            int slash = uploaderUrl.lastIndexOf('/');
            if (slash >= 0 && slash + 1 < uploaderUrl.length()) {
                return uploaderUrl.substring(slash + 1);
            }
            return uploaderUrl;
        }
        return blank(uploaderName, "unknown");
    }

    private static String blank(String value, String fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        return value;
    }

    private static String stamp(int ms) {
        int total = Math.max(ms, 0) / 1000;
        int h = total / 3600;
        int m = (total % 3600) / 60;
        int s = total % 60;
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    private void bindThumb(View row, String url) {
        android.widget.ImageView thumb = row.findViewById(R.id.thumb);
        if (thumb == null) {
            return;
        }
        if (url == null || url.isEmpty()) {
            com.bumptech.glide.Glide.with(row).clear(thumb);
            thumb.setImageDrawable(null);
        } else {
            com.bumptech.glide.Glide.with(row).load(url).centerCrop().into(thumb);
        }
    }

    private final class RelatedAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return related.size();
        }

        @Override
        public Object getItem(int position) {
            return related.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            View row = convert == null
                    ? getLayoutInflater().inflate(R.layout.video_row, parent, false)
                    : convert;
            Browse.Found item = related.get(position);
            ((TextView) row.findViewById(R.id.lib_title)).setText(item.name);
            ((TextView) row.findViewById(R.id.lib_meta)).setText(item.meta);
            bindThumb(row, item.thumb);
            return row;
        }
    }

    private final class ExploreAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return exploreRows.size();
        }

        @Override
        public Object getItem(int position) {
            return exploreRows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            View row = convert == null
                    ? getLayoutInflater().inflate(R.layout.video_row, parent, false)
                    : convert;
            Browse.Found item = exploreRows.get(position);
            TextView heading = row.findViewById(R.id.lib_title);
            TextView meta = row.findViewById(R.id.lib_meta);
            heading.setText(item.name);
            String detail = item.kind;
            if (!item.meta.isEmpty()) {
                detail = item.meta;
            }
            meta.setText(detail);
            bindThumb(row, item.thumb);
            return row;
        }
    }

    private final class DownloadAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return keptVideos.size();
        }

        @Override
        public Object getItem(int position) {
            return keptVideos.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            View row = convert == null
                    ? getLayoutInflater().inflate(R.layout.video_row, parent, false)
                    : convert;
            Shelf.Video video = keptVideos.get(position);
            TextView heading = row.findViewById(R.id.lib_title);
            TextView meta = row.findViewById(R.id.lib_meta);
            heading.setText(video.title);
            meta.setText(video.channel);
            bindThumb(row, video.thumb);
            return row;
        }
    }

    private final class LibraryAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return libraryHits ? hits.size() : shelfLines.size();
        }

        @Override
        public Object getItem(int position) {
            return libraryHits ? hits.get(position) : shelfLines.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            View row = convert == null
                    ? getLayoutInflater().inflate(R.layout.video_row, parent, false)
                    : convert;
            TextView heading = row.findViewById(R.id.lib_title);
            TextView meta = row.findViewById(R.id.lib_meta);
            android.widget.ImageView thumb = row.findViewById(R.id.thumb);
            if (libraryHits) {
                Shelf.Hit hit = hits.get(position);
                String quote = hit.quote == null ? "" : hit.quote;
                heading.setTypeface(null, android.graphics.Typeface.NORMAL);
                setMarks(heading, quote, Query.marks(quote, libraryQuery.getText().toString(), hitConcepts));
                String when = hit.t0 >= 0 ? stamp(hit.t0) + " — " : "";
                meta.setText(when + hit.channel + " — " + hit.title);
                meta.setVisibility(View.VISIBLE);
                thumb.setVisibility(View.VISIBLE);
                bindThumb(row, "");
                return row;
            }
            ShelfLine line = shelfLines.get(position);
            if (line.video == null) {
                heading.setTypeface(null, android.graphics.Typeface.BOLD);
                heading.setText(line.channel);
                meta.setVisibility(View.GONE);
                thumb.setVisibility(View.GONE);
                com.bumptech.glide.Glide.with(row).clear(thumb);
                return row;
            }
            heading.setTypeface(null, android.graphics.Typeface.NORMAL);
            heading.setText(line.video.title);
            meta.setText(line.video.service);
            meta.setVisibility(View.VISIBLE);
            thumb.setVisibility(View.VISIBLE);
            bindThumb(row, line.video.thumb);
            return row;
        }
    }

    /** A shelf row. video is null on the channel heading. */
    private static final class ShelfLine {
        final String channel;
        final Shelf.Video video;

        ShelfLine(String channel) {
            this.channel = channel;
            this.video = null;
        }

        ShelfLine(Shelf.Video video) {
            this.channel = video.channel;
            this.video = video;
        }
    }

    private final class LineAdapter extends BaseAdapter {
        private static final int[] LINE_IDS = {
                R.id.line_0, R.id.line_1, R.id.line_2, R.id.line_3, R.id.line_4
        };
        private final List<Cues.Span> primarySpans;
        private final List<List<Cues.Span>> extras;
        private final List<Prose.Block> blocks;

        LineAdapter(List<Cues.Span> primarySpans, List<List<Cues.Span>> extras, List<Prose.Block> blocks) {
            this.primarySpans = primarySpans;
            this.extras = extras;
            this.blocks = blocks;
        }

        @Override
        public int getCount() {
            return blocks.isEmpty() ? primarySpans.size() : blocks.size();
        }

        @Override
        public Object getItem(int position) {
            return primarySpans.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            View row = convert == null
                    ? getLayoutInflater().inflate(R.layout.line, parent, false)
                    : convert;
            TextView first = row.findViewById(LINE_IDS[0]);
            if (!blocks.isEmpty()) {
                Prose.Block block = blocks.get(position);
                first.setText(stamp(block.t0));
                TextView body = row.findViewById(LINE_IDS[1]);
                body.setTextAppearance(android.R.style.TextAppearance_Medium);
                body.setVisibility(View.VISIBLE);
                if (position == followed) {
                    int[] phrase = Prose.phraseAround(block.text, markStart, markEnd);
                    setUnderlined(body, block.text, phrase[0], phrase[1]);
                } else {
                    body.setText(block.text);
                }
                for (int i = 2; i < LINE_IDS.length; i++) {
                    TextView extra = row.findViewById(LINE_IDS[i]);
                    String text = "";
                    if (i - 2 < extras.size()) {
                        text = Prose.nested(extras.get(i - 2), block.t0, block.t1);
                    }
                    extra.setText(text);
                    extra.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
                }
                return row;
            }
            first.setTextAppearance(android.R.style.TextAppearance_Medium);
            Cues.Span span = primarySpans.get(position);
            String cue = span.text == null ? "" : span.text;
            String line = stamp(span.t0) + "  " + cue;
            if (position == followed) {
                int pad = line.length() - cue.length();
                int[] phrase = Prose.phraseAround(cue, markStart - pad, markEnd - pad);
                setUnderlined(first, line, phrase[0] < 0 ? -1 : phrase[0] + pad,
                        phrase[1] < 0 ? -1 : phrase[1] + pad);
            } else {
                first.setText(line);
            }
            for (int i = 1; i < LINE_IDS.length; i++) {
                TextView view = row.findViewById(LINE_IDS[i]);
                String text = "";
                if (i - 1 < extras.size()) {
                    int at = Cues.overlapping(extras.get(i - 1), span.t0, span.t1);
                    if (at >= 0) {
                        text = extras.get(i - 1).get(at).text;
                    }
                }
                view.setTextAppearance(android.R.style.TextAppearance_Small);
                view.setText(text);
                view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
            }
            return row;
        }
    }

    @Override
    protected void onDestroy() {
        if (lines != null) {
            lines.removeCallbacks(playTick);
        }
        PlayerView view = findViewById(R.id.player);
        if (view != null) {
            view.setPlayer(null);
        }
        shelf.close();
        io.shutdown();
        super.onDestroy();
    }
}
