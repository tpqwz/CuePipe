# cuepipe

An Android client for the videos you keep. A transcript is the work. A channel is the creator. The player opens a place in that work.

NewPipeExtractor supplies streams, caption tracks, channels, and playlists. ExoPlayer plays. The shelf, the reader, the capture queue, and the index are this program. The extractor is GPL-3, so cuepipe is GPL-3.

## Shelf

The library groups videos under a channel. A playlist is one shelf entry that opens onto its videos. Narrow filters by channel, language, year, playlist, and where the text came from: uploader captions, auto captions, on-device recognition, or an edit. Remove drops the transcript and its search entry from the device.

Search uses the Grove query language. Words combine with AND. `OR` and `NOT` are operators only in uppercase. Quotes are a phrase, a trailing `*` is a prefix, and a trailing `~` is one edit. Stemming, Fuzzy, and Concepts do not load a model. Meaning compares the query with paragraph vectors kept in a separate table.

## Index

Search is SQLite FTS4, the same module Grove uses. The table is external content. The body is the normalized transcript. Ranking reads `matchinfo(fts, 'pcnalx')`. Places read `offsets(fts)`: column, query-term index, byte start, and byte length. A span table maps a byte range to a start time and an end time. A hit seeks the span that contains that byte.

## Capture

Uploader captions come first, then auto captions. Auto captions are normalized: a line repeated inside the next overlapping cue is dropped, and cues join into sentences on a pause. The paragraph keeps the first cue's start and the last cue's end. On-device recognition runs only when both tracks are missing, the language is wrong, or you ask for it. That job takes the audio stream and runs in a queue through sherpa-onnx. A model that returns no times does not enter the queue.

Editing changes the words. The times stay unless a span is split or merged. A later recapture replaces automatic text only while the work is still unedited. The source is stored with the work: uploader captions, auto captions, or the recognition model and its language.

## Reader

The reader paints the spans as paragraphs. A tap on a line seeks the player. While the video plays, the span that contains the current time is marked. On a phone the player is a bar pinned to the transcript.

The first slice is one video. Fetch its captions, normalize them to spans, put the channel and the video on the shelf, and seek from a line and from a search hit.
