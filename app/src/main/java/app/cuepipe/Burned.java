package app.cuepipe;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * Reads a burned-in line from the bottom of a kept video file.
 * shortcut: samples twice a second, so a line shorter than that can be missed.
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
                if (text.equals(current)) {
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
