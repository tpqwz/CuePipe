# CuePipe

Android client. Display name CuePipe. Package, database, native library, and this folder stay lowercase `cuepipe` so an already-installed Pixel keeps its library. Public repo `https://github.com/tpqwz/CuePipe`. License GPL-3 because NewPipeExtractor is GPL-3.

A transcript is the work. A channel is the persona. A playlist is a multi-volume shelf. The player opens a place in the work. On-screen captions are optional and start off. A tap on a line seeks: a fresh stream when the video is online, the local file when it is kept.

## How to work

Read the skill that matches the task before editing. ponytail when a change is about to add an abstraction, a dependency, or a new file for one caller. writing-for-agents when editing this file. sanitizers when C or C++ can go out of bounds.

`./gradlew :app:testDebugUnitTest` runs the host tests. `./gradlew :app:assembleDebug` writes `app/build/outputs/apk/debug/app-debug.apk`. Host tests must not load `libcuepipe.so`. `System.loadLibrary` stays inside `Speech.transcribe`.

Install only on the connected Pixel over wireless adb. Rediscover the port with `adb mdns services`. The last serials were `192.168.1.201` and `192.168.1.202` with a changing port. `adb install -r -t` that debug APK. There is no emulator in this workflow. `emulator-5554` is not a target.

Run a long or noisy shell command through `rtk` when it is on `PATH` (`~/.cargo/bin/rtk`). Prefer `/usr/bin/python3`, `/usr/bin/gcc`, and `/usr/bin/ffmpeg`. The `python3`, `ffmpeg`, and `clang` names on `PATH` can be shims. For a second pass on a diff, a parser, or a failing test, ask `qwen2.5-coder:14b` at `http://127.0.0.1:11434`. There is no `qwen` binary on `PATH`. When context-mode is connected, use it for a count, a filter, or a search over a long file or a long log.

Commit when the user asks. Push only when the user asks. Debug keystore APKs only. GitHub Actions (`.github/workflows/build.yml`) installs command line tools, platform android-35, build-tools 35, NDK `28.2.13676358`, and CMake 3.22.1 into `ANDROID_HOME`. The runner image's `ANDROID_SDK_ROOT` is unset for Gradle, because two different SDK paths fail the build. The artifact name is `CuePipe`. Claim a CI run is green only after checking it.

## Build

Java 17. `minSdk` 26, `compileSdk` and `targetSdk` 35. AGP 8.11.1, Gradle 8.14.3. NDK `28.2.13676358`, CMake 3.22.1, ABI `arm64-v8a` only. The Pixel 9a needs 16 KB page alignment. The ONNX library already meets that.

NewPipeExtractor `com.github.teamnewpipe:NewPipeExtractor:v0.27.1` from JitPack. Media3 1.5.1 including `media3-session`, `media3-exoplayer-hls`, and `media3-datasource-okhttp`. OkHttp 4.12. ONNX Runtime Android 1.31.1. ML Kit text recognition and Chinese 16.0.1, translate 17.0.3. `androidx.media:media:1.7.0` so `MediaSessionCompat` is visible. Glide. Material 1.12. AppCompat.

Android, ML Kit, and Firebase groups use an exclusive Aliyun Google mirror so an Aliyun 502 does not poison Maven Central. Keep NewPipeExtractor on v0.27.1. BravePipeExtractor `dev` is extractor 0.26.3. Swapping the jar regresses YouTube trending charts. Copying BitChute and Rumble sources out of that fork does not compile: they need fork-only packages (`search.filter`, `settings`, `brave`). Those trees were removed from `app/src/main/java`. Leave them out.

`NewPipe.init` takes a downloader, not a list of extra services. `NewPipe.getServiceByUrl` only sees `ServiceList` inside the jar. A site that is not YouTube, SoundCloud, PeerTube, Bandcamp, or media.ccc.de is handled in CuePipe before that call, and its name is appended to the drawer spinner. Framatube is already PeerTube. The default PeerTube instance in v0.27.1 is `https://framatube.org`. A pasted PeerTube watch URL uses that URL's host for the API.

## Product surface

The side drawer replaces the bottom bar. It holds the service spinner, Home, Library, Downloads, Excerpts, Subscriptions, Bookmarks, and Check.

Home is the selected service's feed. The search field is also the URL field. Back and More sit with the list. The first page is what loads. `more()` pages a single NewPipe kiosk. Extra services currently load one page and leave `exploreSession` null, so More says there is no further page.

The YouTube home feed concatenates kiosks `trending_music`, `trending_movies_and_shows`, and `trending_gaming`, and the page is labeled Trending. The combined Trending tab throws `ParsingException`. Skip `KioskInfo.getInfo()` and `getName()` (NPE on `YoutubeChannelHelper`). Live is the fallback when those chart ids are absent. Feed load retries `UnknownHostException` three times. Cold start must not prepare a saved googlevideo URL that can no longer resolve. `show(latest, false)` does not prepare. When the shelf has a video, cold start opens the library and does not load a feed. An empty shelf opens Home.

A video page is the player, a thin red progress line, the title, a status line, the channel and Follow, then Paragraphs, Subtitles, and Player. The transcript list fills the rest and follows the playhead. Dragging the list pauses that follow until the next tap or seek. Description, the four caption menus, and find sit behind Details, closed at first.

Player opens a menu: speed (0.5× to 2×), subtitle offset, quality (Auto, 360p, 720p, Audio), Mini or Full, Keep, Burned-in, Recognize, On screen, Translate, Related or Transcript, and Details. Speed survives a new video. Offset resets. A positive offset shows each cue later. Lookup uses `cueClock() = position - offsetMs`. A tap seeks `t0 + offsetMs`. Quality switches the stream and stays at the current moment. Keep saves one file at that quality. Audio saves an audio file. Auto and 720 save the progressive file nearest 720, and 360 the one nearest 360. A picture-only file is the fallback, then audio. An `.m3u8` is skipped. A site NewPipe does not know saves the URL already playing, and refuses an `.m3u8`. Mini shrinks `player_frame` to 168×94 dp, gravity END. The progress line is a 3 dp bar, max 1000, hidden while PlayerView controls are visible.

Library lists saved transcripts under a channel heading. A channel's videos stay together. Newest, title, and channel order the rows inside that run, and the channel keeps the place of its first video. A tap on the heading shows only that channel. Another tap shows every channel. A tap on a video plays it from the stored place: the kept file when there is one, otherwise a fresh stream. A search hit starts at that cue. Filter by service, including the extra sites once a video from that site is saved (`Shelf.services`). Search is FTS4 across every track and returns every matching span, each with its snippet and the cue time. The query word is marked in the accent color. A concept synonym is blue. A short ending (s, es, ed, ing, er, ly) counts as the word. A Porter stem outside that list is not marked. Terms inside one line collapse to one row. The service filter applies to lexical search and to Meaning. Fuzzy, Concepts, and Meaning are checkboxes. Excerpts are a separate list in the drawer, also searchable.

## Playback

`PlayService` owns one process-wide ExoPlayer on the application context, with audio focus and pause on becoming-noisy. The activity calls `setPlayer` and `setPlayer(null)`. It does not `player.release()` in `onDestroy`. The service does not release the player in `onDestroy`. `ACTION_STOP` pauses, clears media items, and `stopSelf`.

Notification channel `play`, id 7. Actions `app.cuepipe.PAUSE`, `PLAY`, `STOP`. MediaSession id `cuepipe`. Foreground type `mediaPlayback`. `startForeground` can throw when notification permission is denied. Catch that and still play. Permissions: `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS` (requested at startup, code 4), `WAKE_LOCK`.

`PlayService.sources()` is an OkHttp data source wrapped in `DefaultDataSource`, so a kept file still plays. OkHttp rejects a `file` URI. Android's `HttpURLConnection` drops Referer and Origin, and Odysee answers 401 without them. A request that would omit Range is sent as `bytes=0-`. Bilibili's CDN (`bilivideo`, `hdslb`, or `akamaized` with `upgcxcode`) answers 403 to that open range. Playback probes `bytes=0-1`, reads the length from `Content-Range`, and requests `bytes=0-(length-1)`. A range that already has an end is left alone. An `.m3u8` whose body starts with gzip magic is decompressed and returned as a 200. Akamai did that to a NASA+ variant playlist. It adds a Referer, and sometimes Origin or a cookie, by host:

| Host | Header |
| --- | --- |
| `bilivideo`, `hdslb`, or `akamaized` with path `upgcxcode` | Referer `https://www.bilibili.com/` |
| `nicovideo.jp` | Firefox user agent, Referer `https://www.nicovideo.jp/`, cookie `PlayService.nicoCookie` |
| `rumble.cloud` | Referer `https://rumble.com/` |
| `live-video.net` or `kick.com` | Referer and Origin `https://kick.com` |
| `odycdn.com` | Referer and Origin `https://odysee.com` |
| `ted.com` or `tedcdn.com` | Referer `https://www.ted.com/` |
| `nasaplus.akamaized.net` | Referer `https://plus.nasa.gov/` |
| `play.redbull.com` | Referer `https://www.redbull.com/` |

Video-only plus a separate audio URL uses `MergingMediaSource`. Skip `.m3u8` when choosing a NewPipe progressive file. A Bilibili picture and its audio are separate. Kick, Niconico, NASA+, and Red Bull play one HLS playlist. Quality `0` is audio. `-1` is Auto. `360` and `720` pick the nearest height that source actually has.

Theme: `colorPrimary` is accent `#e53935` so text buttons are not toolbar gray. `colorPrimaryDark`, the status bar, and the toolbar background are `@color/primary` `#212121`. Text buttons use `colorOnSurface`. Night mode is the expected look. The toolbar stays `@color/primary`, not `?attr/colorPrimary`, or the bar turns red.

## Transcripts

Track sources: `manual`, `auto`, `ocr`, `asr`, `edited`, `translation`. `Shelf.save` drops only unedited `manual` and `auto` tracks and their span, docs, and vec rows. It skips an incoming caption whose language already has an `edited` track. `ocr`, `asr`, and `translation` stay. A second English manual track is not inserted when an edited English track exists.

Capture order when a NewPipe video is opened: uploader captions, auto captions (overlapping lines roll), then the user can run Burned-in or Recognize. Burned-in OCRs the bottom of a file with ML Kit, including the Chinese model. A later sample that shares most of its words extends that cue, so a steady ticker is not split by one misread letter. Running Burned-in or Recognize again replaces the previous track with the same label. Recognize decodes audio with whisper.cpp tiny (`ggml-tiny.bin`, downloaded on first use into filesDir, not vendored). A kept file is used when there is one. Otherwise the stream is downloaded to cache, read, and deleted, and `local_path` stays empty. Segment times are centiseconds (`t0 * 10` = milliseconds). A kept video with no audio track gets a side audio download that is deleted after use and does not replace `local_path`. A Bilibili kept picture has no sound. Recognize then calls `biliAudio`, which signs `playurl` again and downloads the audio track.

Translate uses ML Kit `translate:17.0.3` and writes another track with the same times. Up to four subtitle tracks show at once. The first checked language is the spine. Paragraphs (`Prose`) break on a pause over 1500 ms, or length over 700, or over 480 after a finished sentence. Latin text is capitalized and given periods. CJK is not given Latin periods and is joined without a space when both sides are CJK. The playhead underlines the current clause, through the next comma or sentence stop, in paragraphs, in line mode, and on an on-screen caption. The accent color is not used for that mark. A cue's time is shared across its words by length. CJK is one character at a time. A gap keeps the word just played. There are no word timestamps. Extra languages nest under the paragraph (`line_2` through `line_4`). Long-press in prose offers Play here, Save excerpt, and Share. Line mode also offers Edit line.

Podcasts (`Cast`): a pasted URL that looks like a feed, or whose body contains `<rss` or `<feed`, is parsed with a namespace-aware `DocumentBuilder`. A `<!DOCTYPE` is refused in the text, because Android's parser throws if the Apache doctype feature is set. External entities stay off when the parser allows that feature. Podcast 2.0 `podcast:transcript` becomes a caption track via `Cues.parse`. A JSON transcript is `segments` with `startTime` in seconds, `body`, and an optional `speaker`. An HTML transcript is a `cite`, a `time` clock (`m:ss` or `h:mm:ss`), and a `p`. The last monologue runs four hours because the file has no end. A plain file with a clock at the start of a line keeps that time. A plain file with no clock is one cue that runs four hours. A WebVTT voice tag `<v Ada>` keeps the name. When one language has several files, the timed file is kept and the HTML or plain copy is skipped. `podcast:chapters` becomes a track named chapters. Each title runs until the next start. The last chapter runs four hours because the file has no end. An item needs an enclosure, or a `podcast:alternateEnclosure` source when that is missing. The list shows 40 episodes at a time, and More shows the next 40. Check looks at the first 40 only, so an old back catalog is not reported as new. Kind `episode`. The drawer has RSS and Podcasts. RSS lists every item that has a link, follows the feed with service `RSS`, and a long-press shares the title and the link. Podcasts lists episodes. A long-press shares the title, the page, and the audio address. A tap on a link opens it. Pasting a feed follows the show: the bookmark service is `Podcast`, the url is the feed, and Subscriptions lists it. Share on either panel sends one OPML note of every RSS and podcast feed. Pasting that note, or sharing it into CuePipe, follows them again. The activity is `singleTop` and accepts `ACTION_SEND` of `text/plain`. An audio file uses a short player with the controls left up. A tap reloads the feed. Check parses that feed and lists an episode whose enclosure and page are not already saved. It does not run NewPipe capture on those rows. The channel url stored with an episode is the feed, so Follow on the episode is the same subscription. No transcript: status says to Keep, then Recognize. `keep()` falls through to `keepDirect` when `NewPipe.getServiceByUrl` throws, so an enclosure `.mp3` or `.m4a` downloads. An `.m3u8` Keep is refused with "This stream is a playlist."

## Shelf

Database file `cuepipe.db`, `SQLiteOpenHelper` version 9. `video.place_ms` is where playback last was. A place under 3 seconds is not stored, so opening does not wipe a resume point. Within 4 seconds of the end, the place is cleared. FTS4 external content, `tokenize=porter`. Rebuild with `INSERT INTO fts(fts) VALUES('rebuild')`. The external-content delete command and `content_rowid` fail on the sqlite builds used here. No UPSERT, because of API 26.

Tables: `channel` (column `follow`), `video`, `track`, `span`, `docs`, `fts`, `vec` (paragraph embeddings: track_id, idx, hash, dim, blob), `bookmark`, `excerpt`. Upgrade: before 2 creates bookmark. From 2 until 7, `ALTER` adds `follow` and marks existing `kind='channel'` rows followed. Before 8 creates `excerpt`. Before 6 creates `vec`.

Meaning: Xenova `multilingual-e5-small` quantized ONNX, about 118 MB, plus the SentencePiece unigram vocabulary. Prefixes `query: ` and `passage: `. Mean pool and L2. The model loads only when a library search has Meaning checked. The ONNX telemetry provider is removed, because its startup `loadLibrary` crashes the arm64 translator on an x86_64 emulator. An older English vector is ignored and computed again. Blend 0.55 lexical rank and 0.45 cosine. A vector-only hit is kept when its score is above 0.2. Concepts are a small synonym map, not WordNet. Query syntax matches Grove: AND, uppercase OR and NOT, quotes, prefix `*`, trailing `~`.

Excerpts dedupe on video id, t0, and text. Follow marks a channel. Check uses `follow=1` or a saved playlist.

## Extra sources

Each site is one class next to `MainActivity`. The class parses and builds URLs. The activity does HTTP. Tests are host JUnit and use fixtures, not the network. The drawer appends these names after `NewPipe.getServices()`, in this order. Selection index `exploreServices.size() + n` is that name. A pasted watch URL is recognized from any selected service and opened before the feed load, ahead of the NewPipe call.

| Order | Name | Class | What plays | Captions |
| --- | --- | --- | --- | --- |
| 0 | BitChute | `Chute` | One MP4 from `api.bitchute.com` `api/beta/video/media` | None. Recognize reads a temporary file |
| 1 | Bilibili | `Bili` | WBI-signed `playurl`, fnval 16. Picture and audio are separate. Prefer an `akamaized` URL. `bilivideo.com` returned 403 from this network | Login required, so none |
| 2 | Niconico | `Nico` | Watch page `server-response`, now under `$watchV4.data.media`. POST `access-rights/hls` | None in this slice. Live ids (`lv`) stay out |
| 3 | Rumble | `Rumble` | oEmbed, then the embed page's `ua.mp4` map | None |
| 4 | Kick | `Kick` | Channel `playback_url` HLS for a live channel | None. Saved VODs are not opened |
| 5 | Odysee | `Odysee` | `player.odycdn.com/v6/streams/{claim}/{sdHash6}.mp4` | None |
| 6 | FOSDEM | `Fosdem` | `mp4Url` from `api.opensource-tv.com` | WebVTT saved |
| 7 | Internet Archive | `Archive` | `archive.org/download/{id}/{file}`, nearest height | SRT or VTT, up to four |
| 8 | TED | `Ted` | GraphQL `nativeDownloads`. Auto is high, 720 is medium, 360 is low, Audio is `audioDownload` | WebVTT from HLS metadata, up to four |
| 9 | NASA+ | `Nasa` | WordPress `meta.video-url` HLS | None |
| 10 | Red Bull TV | `RedBull` | `api-player.redbull.com/rbcom/videoresource` HLS | English VTT from the asset id |

BitChute endpoints: `api/beta/videos` popular, `api/beta/search/videos`, `api/beta/channel/videos`, `api/beta9/video`, `api/beta/video/media`. Profile and playlist URLs are not handled.

Bilibili signing: `nav` plus `bili-header.umd.js` builds the mixin key. `finger/spi` supplies `buvid3`. Play is `fnval` 16. Search adds the plugin's `dm_img` fingerprint fields. The mixin and buvid are cached on the activity for the process. The header script is about 1.7 MB and is fetched once.

Niconico: the Chrome Android user agent gets 403 on the watch page. Fetch and playback use a Firefox user agent. The playlist 403s without the `nicosid` cookie from that page. Store it in `PlayService.nicoCookie` before `play`. The plugin's v1 search returns 404, so search is v2. `sm47057757` opened on the Pixel and access-rights returned an HLS URL on `delivery.domand.nicovideo.jp`. CloudFront at `JFK50` answered 403 for that URL from this network, with the cookie, a Firefox user agent, and Referer. The signed policy only has an expiry. yt-dlp marks the site Japan-only.

Rumble: `rumble.com/videos` and `embedJS` return Cloudflare `cf-mitigated: challenge` from this network and from the Pixel's `HttpURLConnection`. The embed page is not challenged and contains the same `ua.mp4` list. A watch link's oEmbed (`/api/Media/oembed.json?url=`) points at that embed. Home and text search tell the user to paste a link, and clear the list that was on screen. A home or search that throws does the same. More, and opening a channel from a row, keep the list and only change the status line. `v180wvd` played on the Pixel from that map. The page has no captions, so the status says to Keep, then Recognize.

Kick: home is `web.kick.com` featured livestreams. Search is `search.kick.com`. A `kick.com/{slug}` link loads `api/v2/channels/{slug}`. An empty `playback_url` means the channel is not live. `/videos/` links are not channels. `kick.com/kaneljoseph` played on the Pixel: the HLS ladder reached 1080p, audio started, status "Live. No transcript."

Odysee: `odysee-api` builds a free stream as `/v6/streams/{claimId}/{first 6 hex of sd_hash}.mp4`. `player-server` asks for an edge token when the claim is tagged `c:members-only`, `c:unlisted`, `c:purchase`, `c:rental`, or a purchase, rental, or scheduled tag. Those claims are refused with "needs an Odysee login". Unsigned `get` returns `missing required signature param` for unlisted claims. Resolve and `claim_search` on `api.na-backend.odysee.com` work without a login. A watch URL `@Channel:id/name:id` on `odysee.com` becomes `lbry://@Channel#id/name#id`. `videoUri` ignores every other host. A slash path on FOSDEM or NASA used to be resolved as a claim and failed with "no video" on the previous title.

FOSDEM: `api.opensource-tv.com` events and search. A schedule URL or a `video.fosdem.org` file URL names the year and slug.

Internet Archive: `advancedsearch.php` for the list, `metadata/{id}` for files. Skip `source=metadata`. Prefer the height nearest the quality target. Home is `mediatype:(movies OR video)` sorted by downloads, so the first rows can be odd popular items.

TED: GraphQL `https://www.ted.com/graphql`. Search moved to `https://www.ted.com/api/search` (the plugin's `zenith-prod-alt.ted.com` host redirects). A talk URL is `ted.com/talks/{slug}`.

NASA+: `https://plus.nasa.gov/wp-json/wp/v2/video`. Titles are HTML-escaped. One HLS playlist. `plus.nasa.gov/video/moon-base-update-lunar-rovers/` played on the Pixel until a variant playlist, `17935-4.m3u8`, came back HTTP 206 with a gzip body. The HLS parser then failed because those bytes are not `#EXTM3U`. Playback gunzips an `.m3u8` whose body starts with gzip magic and hands it on as a 200.

Red Bull: the feed 403s on a plain client user agent. From this network it also 403s with the Firefox user agent (`Nico.UA`), Akamai Access Denied. The player API still answers. Row URLs carry `?video=` plus the `rrn:content:` id, because a public `/films/{slug}` page does not include that id. Shows are skipped. A pasted `?video=rrn:content:episode-videos:` link played on the Pixel: ABC of… Hard Enduro, picture and sound, one English caption track.

## Grayjay

`https://grayjay-sources.github.io/` is a catalog (`sources.json`, on the order of 119 plugins). Official mirrors live under `futo-org/grayjay-plugin-*`. Plugins are JavaScript against Grayjay's host (`http`, `DOMParser`). They do not load inside NewPipeExtractor. Read a plugin to learn the HTTP, then reimplement that HTTP in a CuePipe class. Keep the plugin's license notice in mind: Bilibili is MPL-2.0. Niconico, Odysee, BitChute, Rumble, and PeerTube plugins are AGPL-3. AGPL-3 section 13 allows combining with GPL-3. Plugin-derived files stay under their license. The rest of CuePipe stays GPL-3.

The Grayjay app is FUTO Source First License 1.1, non-commercial, not GPL. Leave the app, its engine, and its screens out.

These plugins were read and did not become a drawer row:

- Dailymotion player metadata returns an HLS URL. The CDN answers 403 unless the client impersonates a browser TLS fingerprint.
- Twitch playback in the plugin depends on a Kasada integrity token.
- Rumble's own listing and `embedJS`, as above.

## Native speech

`app/src/main/cpp/CMakeLists.txt` fetches whisper.cpp `v1.9.5` with FetchContent and builds shared library `cuepipe` from `whisper_jni.c`. The model is not in the repo. A host format test can be built with `-DCUEPIPE_FORMAT_TEST`. ASan and UBSan for that path use `/usr/bin/gcc`, not the `clang` shim.

## What is still open

Internet Archive, TED, BitChute, Odysee, Bilibili, FOSDEM, Rumble, Kick, NASA+, and Red Bull TV were watched playing on the Pixel. Bilibili picture and audio play together after the closed byte range. The FOSDEM check was Kevin Schulmeister, Closing of the Decentralized Internet devroom, about a minute in with the file buffered. The Rumble check was Mr Producer Media, 911 Audio, picture and sound moving, status "No transcript. Keep, then Recognize." The Kick check was the live channel kaneljoseph, picture on screen and audio started, status "Live. No transcript." A pasted watch URL for every extra site opens before the feed load. Niconico opened on the Pixel and the stream was refused with HTTP 403. NASA+ on the gunzip build played the lunar video with audio and did not throw the playlist parser error. The Red Bull check was ABC of… Hard Enduro, picture and sound moving, one caption track, the playhead on the current line. Bluetooth sheets and the notification shade often cover uiautomator dumps. Leave the user's shade alone.

Parakeet or sherpa-onnx wait until the engine returns start and end times.

Drawer indexes for the extra services are `exploreServices.size() + n` in the order listed above. Adding a name in the middle renumbers every later `*Selected()` check.
