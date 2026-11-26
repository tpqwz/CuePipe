package app.cuepipe;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * Reads a burned-in line from the bottom of a kept video file.
 * shortcut: samples twice a second, so a line shorter than that can be missed.
 * A later sample that shares most of its words extends the cue instead of
 * starting another one, so a steady ticker is not split by a misread letter.
 */
public final class Burned {
    private Burned() {
    }

    public static Cues.Transcript read(String path, IntConsumer progress) throws Exception {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        TextRecognizer chinese = TextRecognition.getClient(
                new ChineseTextRecognizerOptions.Builder().build());
        TextRecognizer latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try {
            retriever.setDataSource(path);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long durationMs = duration == null ? 0 : Long.parseLong(duration);
            StringBuilder vtt = new StringBuilder("WEBVTT\n\n");
            String current = "";
            int start = 0;
            int step = 500;
            for (long t = 0; t <= durationMs; t += step) {
                if (progress != null && durationMs > 0) {
                    progress.accept((int) (t * 100 / durationMs));
                }
                Bitmap frame = retriever.getFrameAtTime(
                        t * 1000, MediaMetadataRetriever.OPTION_CLOSEST);
                if (frame == null) {
                    continue;
                }
                int top = (int) (frame.getHeight() * 0.78);
                int height = frame.getHeight() - top;
                Bitmap band = height > 0
                        ? Bitmap.createBitmap(frame, 0, top, frame.getWidth(), height)
                        : frame;
                String text = recognize(chinese, band);
                if (text.isEmpty()) {
                    text = recognize(latin, band);
                }
                if (band != frame) {
                    band.recycle();
                }
                frame.recycle();
                if (text.isEmpty() && current.isEmpty()) {
                    continue;
                }
                if (!text.isEmpty() && sameLine(text, current)) {
                    if (words(text).size() > words(current).size()) {
                        current = text;
                    }
                    continue;
                }
                if (!current.isEmpty()) {
                    cue(vtt, start, (int) t, current);
                }
                current = text;
                start = (int) t;
            }
            if (!current.isEmpty()) {
                cue(vtt, start, (int) durationMs, current);
            }
            Cues.Transcript transcript = Cues.parse(vtt.toString(), "vtt", false);
            return new Cues.Transcript("ocr", transcript.body, transcript.spans);
        } finally {
            retriever.release();
            chinese.close();
            latin.close();
        }
    }

    /** True when two readings are the same caption with a few letters off. */
    static boolean sameLine(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        Set<String> left = words(a);
        Set<String> right = words(b);
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        int shared = 0;
        for (String word : left) {
            if (right.contains(word)) {
                shared++;
            }
        }
        int larger = Math.max(left.size(), right.size());
        return shared * 100 / larger >= 60;
    }

    /** Words of four letters or more, so a stray OCR mark does not count. */
    private static Set<String> words(String text) {
        Set<String> out = new HashSet<>();
        StringBuilder word = new StringBuilder();
        String lower = text.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); ) {
            int cp = lower.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isLetter(cp)) {
                word.appendCodePoint(cp);
            } else if (word.length() >= 4) {
                out.add(word.toString());
                word.setLength(0);
            } else {
                word.setLength(0);
            }
        }
        if (word.length() >= 4) {
            out.add(word.toString());
        }
        return out;
    }

    private static void cue(StringBuilder vtt, int t0, int t1, String text) {
        vtt.append(stamp(t0)).append(" --> ").append(stamp(Math.max(t1, t0 + 1))).append('\n');
        vtt.append(text).append("\n\n");
    }

    private static String stamp(int ms) {
        int total = Math.max(ms, 0);
        int h = total / 3600000;
        int m = (total / 60000) % 60;
        int s = (total / 1000) % 60;
        int milli = total % 1000;
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", h, m, s, milli);
    }

    private static String recognize(TextRecognizer recognizer, Bitmap bitmap) throws Exception {
        String text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).getText();
        if (text == null) {
            return "";
        }
        return text.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }
}
