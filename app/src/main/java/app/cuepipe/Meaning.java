package app.cuepipe;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * English E5, fetched once into app files. The switch stays off until a search uses it.
 * shortcut: e5-small-v2 is English WordPiece. A multilingual model needs SentencePiece.
 */
public final class Meaning {
    private static final String MODEL_URL =
            "https://huggingface.co/Xenova/e5-small-v2/resolve/main/onnx/model_quantized.onnx";
    private static final String VOCAB_URL =
            "https://huggingface.co/Xenova/e5-small-v2/resolve/main/vocab.txt";
    private static final int MAX_TOKENS = 128;

    private static OrtEnvironment environment;
    private static OrtSession session;
    private static Near.Vocab vocab;
    private static boolean types;

    private Meaning() {
    }

    public static float[] query(Context context, String text, IntConsumer progress) throws IOException {
        return embed(context, "query: " + text, progress);
    }

    public static float[] passage(Context context, String text) throws IOException {
        return embed(context, "passage: " + text, null);
    }

    private static synchronized float[] embed(Context context, String text, IntConsumer progress)
            throws IOException {
        ensure(context, progress);
        int[] ids = Near.encode(vocab, text, MAX_TOKENS);
        long[] id64 = new long[ids.length];
        long[] mask = new long[ids.length];
        long[] type = new long[ids.length];
        for (int i = 0; i < ids.length; i++) {
            id64[i] = ids[i];
            mask[i] = 1;
        }
        OnnxTensor idTensor = null;
        OnnxTensor maskTensor = null;
        OnnxTensor typeTensor = null;
        OrtSession.Result result = null;
        try {
            Map<String, OnnxTensor> inputs = new HashMap<>();
            idTensor = tensor(id64);
            maskTensor = tensor(mask);
            inputs.put("input_ids", idTensor);
            inputs.put("attention_mask", maskTensor);
            if (types) {
                typeTensor = tensor(type);
                inputs.put("token_type_ids", typeTensor);
            }
            result = session.run(inputs);
            float[][][] hidden = (float[][][]) result.get(0).getValue();
            return Near.unit(pool(hidden[0], mask));
        } catch (OrtException e) {
            throw new IOException(e.getMessage(), e);
        } finally {
            if (idTensor != null) {
                idTensor.close();
            }
            if (maskTensor != null) {
                maskTensor.close();
            }
            if (typeTensor != null) {
                typeTensor.close();
            }
            if (result != null) {
                result.close();
            }
        }
    }

    private static OnnxTensor tensor(long[] values) throws OrtException {
        return OnnxTensor.createTensor(environment, LongBuffer.wrap(values), new long[]{1, values.length});
    }

    private static float[] pool(float[][] tokens, long[] mask) {
        float[] sum = new float[tokens[0].length];
        int count = 0;
        int length = Math.min(tokens.length, mask.length);
        for (int t = 0; t < length; t++) {
            if (mask[t] == 0) {
                continue;
            }
            for (int d = 0; d < sum.length; d++) {
                sum[d] += tokens[t][d];
            }
            count++;
        }
        if (count == 0) {
            count = 1;
        }
        for (int d = 0; d < sum.length; d++) {
            sum[d] /= count;
        }
        return sum;
    }

    private static void ensure(Context context, IntConsumer progress) throws IOException {
        if (session != null && vocab != null) {
            return;
        }
        File model = fetch(context, "e5-small-v2.onnx", MODEL_URL, 20_000_000, progress);
        File words = fetch(context, "e5-vocab.txt", VOCAB_URL, 100_000, null);
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(words))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        vocab = Near.vocab(lines);
        try {
            environment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setIntraOpNumThreads(2);
            session = environment.createSession(model.getAbsolutePath(), options);
            options.close();
            types = session.getInputNames().contains("token_type_ids");
        } catch (OrtException e) {
            model.delete();
            session = null;
            vocab = null;
            throw new IOException(e.getMessage(), e);
        }
    }

    private static File fetch(Context context, String name, String url, long minBytes, IntConsumer progress)
            throws IOException {
        File dest = new File(context.getFilesDir(), name);
        if (dest.isFile() && dest.length() > minBytes) {
            return dest;
        }
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }
            long total = response.body().contentLength();
            File tmp = new File(context.getFilesDir(), name + ".part");
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
            if (tmp.length() < minBytes || !tmp.renameTo(dest)) {
                tmp.delete();
                throw new IOException(name);
            }
        }
        return dest;
    }
}
