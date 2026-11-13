# CuePipe

Android client. Display name CuePipe. Package, database, native library, and this folder stay lowercase `cuepipe` so an already-installed Pixel keeps its library. Public repo `https://github.com/tpqwz/CuePipe`. License GPL-3 because NewPipeExtractor is GPL-3.

A transcript is the work. A channel is the persona. A playlist is a multi-volume shelf. The player opens a place in the work. On-screen captions are optional and start off. A tap on a line seeks: a fresh stream when the video is online, the local file when it is kept.

## How to work

Read the skill that matches the task before editing. ponytail when a change is about to add an abstraction, a dependency, or a new file for one caller. writing-for-agents when editing this file. sanitizers when C or C++ can go out of bounds.

`./gradlew :app:testDebugUnitTest` runs the host tests. `./gradlew :app:assembleDebug` writes `app/build/outputs/apk/debug/app-debug.apk`. Host tests must not load `libcuepipe.so`. `System.loadLibrary` stays inside `Speech.transcribe`.

Install only on the connected Pixel over wireless adb. Rediscover the port with `adb mdns services`. The last serials were `192.168.1.201` and `192.168.1.202` with a changing port. `adb install -r -t` that debug APK. There is no emulator in this workflow. `emulator-5554` is not a target.

Run a long or noisy shell command through `rtk` when it is on `PATH` (`~/.cargo/bin/rtk`). Prefer `/usr/bin/python3`, `/usr/bin/gcc`, and `/usr/bin/ffmpeg`. The `python3`, `ffmpeg`, and `clang` names on `PATH` can be shims. For a second pass on a diff, a parser, or a failing test, ask `qwen2.5-coder:14b` at `http://127.0.0.1:11434`. There is no `qwen` binary on `PATH`. When context-mode is connected, use it for a count, a filter, or a search over a long file or a long log.

Commit when the user asks. Push only when the user asks. Debug keystore APKs only. GitHub Actions (`.github/workflows/build.yml`) installs command line tools, platform android-35, build-tools 35, NDK `28.2.13676358`, and CMake 3.22.1. The artifact name is `CuePipe`. Claim a CI run is green only after checking it.

## Build

Java 17. `minSdk` 26, `compileSdk` and `targetSdk` 35. AGP 8.11.1, Gradle 8.14.3. NDK `28.2.13676358`, CMake 3.22.1, ABI `arm64-v8a` only. The Pixel 9a needs 16 KB page alignment. The ONNX library already meets that.

NewPipeExtractor `com.github.teamnewpipe:NewPipeExtractor:v0.27.1` from JitPack. Media3 1.5.1 including `media3-session` and `media3-exoplayer-hls`. OkHttp 4.12. ONNX Runtime Android 1.31.1. ML Kit text recognition and Chinese 16.0.1, translate 17.0.3. `androidx.media:media:1.7.0` so `MediaSessionCompat` is visible. Glide. Material 1.12. AppCompat.

Android, ML Kit, and Firebase groups use an exclusive Aliyun Google mirror so an Aliyun 502 does not poison Maven Central. Keep NewPipeExtractor on v0.27.1. BravePipeExtractor `dev` is extractor 0.26.3. Swapping the jar regresses YouTube trending charts. Copying BitChute and Rumble sources out of that fork does not compile: they need fork-only packages (`search.filter`, `settings`, `brave`). Those trees were removed from `app/src/main/java`. Leave them out.

`NewPipe.init` takes a downloader, not a list of extra services. `NewPipe.getServiceByUrl` only sees `ServiceList` inside the jar. A site that is not YouTube, SoundCloud, PeerTube, Bandcamp, or media.ccc.de is handled in CuePipe before that call, and its name is appended to the drawer spinner. Framatube is already PeerTube. The default PeerTube instance in v0.27.1 is `https://framatube.org`. A pasted PeerTube watch URL uses that URL's host for the API.

## Product surface

The side drawer replaces the bottom bar. It holds the service spinner, Home, Library, Downloads, Excerpts, Subscriptions, Bookmarks, and Check.

Home is the selected service's feed. The search field is also the URL field. Back and More sit with the list. The first page is what loads. `more()` pages a single NewPipe kiosk. Extra services currently load one page and leave `exploreSession` null, so More says there is no further page.

The YouTube home feed concatenates kiosks `trending_music`, `trending_movies_and_shows`, and `trending_gaming`, and the page is labeled Trending. The combined Trending tab throws `ParsingException`. Skip `KioskInfo.getInfo()` and `getName()` (NPE on `YoutubeChannelHelper`). Live is the fallback when those chart ids are absent. Feed load retries `UnknownHostException` three times. Cold start must not prepare a saved googlevideo URL that can no longer resolve. `show(latest, false)` does not prepare. When the shelf has a video, cold start opens the library and does not load a feed. An empty shelf opens Home.

A video page is the player, a thin red progress line, the title, a status line, the channel and Follow, then Paragraphs, Subtitles, and Player. The transcript list fills the rest and follows the playhead. Dragging the list pauses that follow until the next tap or seek. Description, the four caption menus, and find sit behind Details, closed at first.

Player opens a menu: speed (0.5× to 2×), subtitle offset, quality (Auto, 360p, 720p, Audio), Mini or Full, Keep, Burned-in, Recognize, On screen, Translate, Related or Transcript, and Details. Speed survives a new video. Offset resets. A positive offset shows each cue later. Lookup uses `cueClock() = position - offsetMs`. A tap seeks `t0 + offsetMs`. Quality switches the stream and stays at the current moment. Mini shrinks `player_frame` to 168×94 dp, gravity END. The progress line is a 3 dp bar, max 1000, hidden while PlayerView controls are visible.

Library lists saved transcripts under a channel heading. A channel's videos stay together. Newest, title, and channel order the rows inside that run, and the channel keeps the place of its first video. A tap on the heading shows only that channel. Another tap shows every channel. Filter by service, including the extra sites once a video from that site is saved (`Shelf.services`). Search is FTS4 across every track and returns every matching span, each with its snippet and the cue time. The query word is marked in the accent color. A concept synonym is blue. A short ending (s, es, ed, ing, er, ly) counts as the word. A Porter stem outside that list is not marked. Terms inside one line collapse to one row. The service filter applies to lexical search and to Meaning. Fuzzy, Concepts, and Meaning are checkboxes. Excerpts are a separate list in the drawer, also searchable.

## Playback

`PlayService` owns one process-wide ExoPlayer on the application context, with audio focus and pause on becoming-noisy. The activity calls `setPlayer` and `setPlayer(null)`. It does not `player.release()` in `onDestroy`. The service does not release the player in `onDestroy`. `ACTION_STOP` pauses, clears media items, and `stopSelf`.

Notification channel `play`, id 7. Actions `app.cuepipe.PAUSE`, `PLAY`, `STOP`. MediaSession id `cuepipe`. Foreground type `mediaPlayback`. `startForeground` can throw when notification permission is denied. Catch that and still play. Permissions: `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS` (requested at startup, code 4), `WAKE_LOCK`.

`PlayService.sources()` is the data source factory. It adds a Referer, and sometimes Origin or a cookie, by host:

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

Capture order when a NewPipe video is opened: uploader captions, auto captions (overlapping lines roll), then the user can run Burned-in or Recognize. Burned-in OCRs the bottom of a kept file with ML Kit, including the Chinese model. Recognize decodes kept audio with whisper.cpp tiny (`ggml-tiny.bin`, downloaded on first use into filesDir, not vendored). Segment times are centiseconds (`t0 * 10` = milliseconds). A kept video with no audio track gets a side audio download that is deleted after use and does not replace `local_path`. A Bilibili kept picture has no sound. Recognize then calls `biliAudio`, which signs `playurl` again and downloads the audio track.

Translate uses ML Kit `translate:17.0.3` and writes another track with the same times. Up to four subtitle tracks show at once. The first checked language is the spine. Paragraphs (`Prose`) break on a pause over 1500 ms, or length over 700, or over 480 after a finished sentence. Latin text is capitalized and given periods. CJK is not given Latin periods and is joined without a space when both sides are CJK. The playhead marks the current word in the accent color, in the list and on an on-screen caption. A cue's time is shared across its words by length. CJK is one character at a time. A gap keeps the word just played. There are no word timestamps. Extra languages nest under the paragraph (`line_2` through `line_4`). Long-press in prose offers Play here, Save excerpt, and Share. Line mode also offers Edit line.

Podcasts (`Cast`): a pasted URL that looks like a feed, or whose body contains `<rss` or `<feed`, is parsed with a namespace-aware `DocumentBuilder`. DOCTYPE and external entities are off. Podcast 2.0 `podcast:transcript` becomes a caption track via `Cues.parse`. The first 40 items with an enclosure are listed. Kind `episode`. No transcript: status says to Keep, then Recognize. `keep()` falls through to `keepDirect` when `NewPipe.getServiceByUrl` throws, so an enclosure `.mp3` or `.m4a` downloads. An `.m3u8` Keep is refused with "This stream is a playlist."

## Shelf

Database file `cuepipe.db`, `SQLiteOpenHelper` version 8. FTS4 external content, `tokenize=porter`. Rebuild with `INSERT INTO fts(fts) VALUES('rebuild')`. The external-content delete command and `content_rowid` fail on the sqlite builds used here. No UPSERT, because of API 26.

Tables: `channel` (column `follow`), `video`, `track`, `span`, `docs`, `fts`, `vec` (paragraph embeddings: track_id, idx, hash, dim, blob), `bookmark`, `excerpt`. Upgrade: before 2 creates bookmark. From 2 until 7, `ALTER` adds `follow` and marks existing `kind='channel'` rows followed. Before 8 creates `excerpt`. Before 6 creates `vec`.

Meaning: Xenova `e5-small-v2` quantized ONNX, about 34 MB, plus vocab. WordPiece. Prefixes `query: ` and `passage: `. Mean pool and L2. English only. The model loads only when a library search has Meaning checked. Blend 0.55 lexical rank and 0.45 cosine. A vector-only hit is kept when its score is above 0.2. Concepts are a small synonym map, not WordNet. Query syntax matches Grove: AND, uppercase OR and NOT, quotes, prefix `*`, trailing `~`.

Excerpts dedupe on video id, t0, and text. Follow marks a channel. Check uses `follow=1` or a saved playlist.

## Extra sources

Each site is one class next to `MainActivity`. The class parses and builds URLs. The activity does HTTP. Tests are host JUnit and use fixtures, not the network. The drawer appends these names after `NewPipe.getServices()`, in this order. Selection index `exploreServices.size() + n` is that name. A pasted URL is recognized from any selected service, before the NewPipe call.

| Order | Name | Class | What plays | Captions |
| --- | --- | --- | --- | --- |
| 0 | BitChute | `Chute` | One MP4 from `api.bitchute.com` `api/beta/video/media` | None. Keep, then Recognize |
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

Niconico: the Chrome Android user agent gets 403 on the watch page. Fetch and playback use a Firefox user agent. The playlist 403s without the `nicosid` cookie from that page. Store it in `PlayService.nicoCookie` before `play`. The plugin's v1 search returns 404, so search is v2.

Rumble: `rumble.com/videos` and `embedJS` return Cloudflare `cf-mitigated: challenge` from this network and from the Pixel's `HttpURLConnection`. The embed page is not challenged and contains the same `ua.mp4` list. A watch link's oEmbed (`/api/Media/oembed.json?url=`) points at that embed. Home and text search tell the user to paste a link.

Kick: home is `web.kick.com` featured livestreams. Search is `search.kick.com`. A `kick.com/{slug}` link loads `api/v2/channels/{slug}`. An empty `playback_url` means the channel is not live. `/videos/` links are not channels.

Odysee: `odysee-api` builds a free stream as `/v6/streams/{claimId}/{first 6 hex of sd_hash}.mp4`. `player-server` asks for an edge token when the claim is tagged `c:members-only`, `c:unlisted`, `c:purchase`, `c:rental`, or a purchase, rental, or scheduled tag. Those claims are refused with "needs an Odysee login". Unsigned `get` returns `missing required signature param` for unlisted claims. Resolve and `claim_search` on `api.na-backend.odysee.com` work without a login. A watch URL `@Channel:id/name:id` becomes `lbry://@Channel#id/name#id`.

FOSDEM: `api.opensource-tv.com` events and search. A schedule URL or a `video.fosdem.org` file URL names the year and slug.

Internet Archive: `advancedsearch.php` for the list, `metadata/{id}` for files. Skip `source=metadata`. Prefer the height nearest the quality target. Home is `mediatype:(movies OR video)` sorted by downloads, so the first rows can be odd popular items.

TED: GraphQL `https://www.ted.com/graphql`. Search moved to `https://www.ted.com/api/search` (the plugin's `zenith-prod-alt.ted.com` host redirects). A talk URL is `ted.com/talks/{slug}`.

NASA+: `https://plus.nasa.gov/wp-json/wp/v2/video`. Titles are HTML-escaped. One HLS playlist.

Red Bull: the feed 403s on a plain client user agent and returns JSON with a Firefox user agent (`Nico.UA` is that string). Row URLs carry `?video=` plus the `rrn:content:` id, because a public `/films/{slug}` page does not include that id. Shows are skipped.

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

Internet Archive was watched playing on the Pixel. `archive.org/details/BigBuckBunny_124` reached about 30 seconds with the session state playing. A pasted Archive URL is recognized before the background search. The other extra sites were still only checked by fetching a media URL. Bluetooth sheets and the notification shade often cover uiautomator dumps. Leave the user's shade alone.

Parakeet or sherpa-onnx wait until the engine returns start and end times. Meaning stays English-only.

Drawer indexes for the extra services are `exploreServices.size() + n` in the order listed above. Adding a name in the middle renumbers every later `*Selected()` check.
