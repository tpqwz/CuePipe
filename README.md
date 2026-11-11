# cuepipe

An Android client for the videos you keep. A transcript is the work. A channel is the creator. The player opens a place in that work.

NewPipeExtractor supplies streams, caption tracks, channels, and playlists for every service it knows: YouTube, SoundCloud, PeerTube, Bandcamp, and media.ccc.de. ExoPlayer plays. A site that only a PipePipe fork extracts is outside this extractor. The shelf, the reader, the capture queue, and the index are this program. The extractor is GPL-3, so cuepipe is GPL-3.

## Shelf

The library groups videos under a channel. A playlist is one shelf entry that opens onto its videos. Narrow filters by channel, language, year, playlist, and where the text came from: uploader captions, auto captions, on-device recognition, or an edit. Remove drops the transcript and its search entry from the device.

Search uses the Grove query language. Words combine with AND. `OR` and `NOT` are operators only in uppercase. Quotes are a phrase, a trailing `*` is a prefix, and a trailing `~` is one edit. Stemming, Fuzzy, and Concepts do not load a model. Meaning compares the query with paragraph vectors kept in a separate table.

## Index

Search is SQLite FTS4, the same module Grove uses. The table is external content. The body is the normalized transcript. Ranking reads `matchinfo(fts, 'pcnalx')`. Places read `offsets(fts)`: column, query-term index, byte start, and byte length. A span table maps a byte range to a start time and an end time. A hit seeks the span that contains that byte.

## Capture

Every caption track the extractor offers is kept. Four spinners choose which tracks sit on the list together, up to four at once. A line in the first track shows the lines from the others that cover the same time. On screen draws those current lines on the picture. The switch starts off. A tap seeks that time. Uploader captions and auto captions are separate tracks. A line whose text is contained in the next overlapping cue is dropped, and the kept cue takes the earlier start. On-device recognition runs only when both tracks are missing, the language is wrong, or you ask for it. That job takes the audio stream and runs in a queue through sherpa-onnx. A model that returns no times does not enter the queue.

Editing changes the words. The times stay unless a span is split or merged. A later recapture replaces automatic text only while the work is still unedited. The source is stored with the work: uploader captions, auto captions, or the recognition model and its language.

## Reader

The reader paints the spans as paragraphs. A tap on a line seeks the player. While the video plays, the span that contains the current time is marked. On a phone the player is a bar pinned to the transcript.

## Build

This tree is that first slice, as its own app. It uses NewPipeExtractor for the stream and the caption track. It is not a fork of the PipePipe UI.

```sh
./gradlew :app:test
./gradlew :app:assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. The extractor is fetched from JitPack, which compiles it on the first build. Paste a URL from any service the extractor knows, fetch, and pick up to four caption tracks when the video has them. A tap or a search hit seeks the player. Stemming, Fuzzy, Concepts, Meaning, and on-device recognition are not in this slice.
