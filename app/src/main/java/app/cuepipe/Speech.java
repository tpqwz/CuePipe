package app.cuepipe;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.function.IntConsumer;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** On-device Whisper.cpp. The tiny model is downloaded once into app files. */
public final class Speech {
    private static final String MODEL_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin";

    private static boolean loaded;

    private Speech() {
    }

    /** A timed transcript plus the language code Whisper reported. */
    public static final class Heard {
        public final String lang;
        public final Cues.Transcript transcript;

        Heard(String lang, Cues.Transcript transcript) {
            this.lang = lang;
            this.transcript = transcript;
        }
    }

    public static Heard transcribe(Context context, float[] samples, IntConsumer progress)
            throws IOException {
        loadNative();
        File model = ensureModel(context, progress);
        long ctx = init(model.getAbsolutePath());
        if (ctx == 0) {
            model.delete();
            throw new IOException("whisper model");
        }
        try {
            if (progress != null) {
                progress.accept(-1);
            }
            // shortcut: one float array for the whole file. A long video can run out of memory.
            String raw = transcribe(ctx, samples);
            return new Heard(languageOf(raw), cues(raw));
        } finally {
            freeContext(ctx);
        }
    }

    private static synchronized void loadNative() {
        if (!loaded) {
            System.loadLibrary("cuepipe");
            loaded = true;
        }
    }

    static String languageOf(String raw) {
        if (raw == null) {
            return "und";
        }
        for (String line : raw.split("\n")) {
            if (line.startsWith("lang\t")) {
                String code = line.substring(5).trim();
                return code.isEmpty() ? "und" : code;
            }
        }
        return "und";
    }

    static Cues.Transcript cues(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new Cues.Transcript("asr", "", Collections.emptyList());
        }
        StringBuilder vtt = new StringBuilder("WEBVTT\n\n");
        for (String line : raw.split("\n")) {
            String[] parts = line.split("\t", 3);
            if (parts.length < 3) {
                continue;
            }
            try {
                int t0 = Integer.parseInt(parts[0].trim());
                int t1 = Integer.parseInt(parts[1].trim());
                String text = parts[2].trim();
                if (text.isEmpty()) {
                    continue;
                }
                vtt.append(stamp(t0)).append(" --> ").append(stamp(Math.max(t1, t0 + 1))).append('\n');
                vtt.append(text).append("\n\n");
            } catch (NumberFormatException ignored) {
                // Skip a malformed segment.
            }
        }
        Cues.Transcript parsed = Cues.parse(vtt.toString(), "vtt", true);
        return new Cues.Transcript("asr", parsed.body, parsed.spans);
    }

    private static String stamp(int ms) {
        int total = Math.max(ms, 0);
        int h = total / 3600000;
        int m = (total / 60000) % 60;
        int s = (total / 1000) % 60;
        int milli = total % 1000;
        return String.format(java.util.Locale.US, "%02d:%02d:%02d.%03d", h, m, s, milli);
    }

    private static File ensureModel(Context context, IntConsumer progress) throws IOException {
        File model = new File(context.getFilesDir(), "ggml-tiny.bin");
        if (model.isFile() && model.length() > 1_000_000) {
            return model;
        }
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder().url(MODEL_URL).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }
            long total = response.body().contentLength();
            File tmp = new File(context.getFilesDir(), "ggml-tiny.bin.part");
            try (InputStream in = response.body().byteStream();
                 FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int n;
                long got = 0;
                int last = -1;
                while ((n = in.read(buf)) >= 0) {
                    out.write(buf, 0, n);
                    got += n;
                    if (progress != null && total > 0) {
                        int pct = (int) (got * 100 / total);
                        if (pct != last) {
                            last = pct;
                            progress.accept(pct);
                        }
                    }
                }
            }
            if (!tmp.renameTo(model)) {
                throw new IOException("model rename");
            }
        }
        return model;
    }

    private static native long init(String path);

    private static native String transcribe(long ctx, float[] samples);

    private static native void freeContext(long ctx);
}
