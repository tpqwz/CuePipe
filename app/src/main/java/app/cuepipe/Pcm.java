package app.cuepipe;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Decodes an audio or video file to 16 kHz mono float samples for Whisper. */
public final class Pcm {
    private Pcm() {
    }

    /** shortcut: the decoded file is one array. A long video can run out of memory. */
    public static float[] decode16kMono(String path) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(path);
        int track = -1;
        MediaFormat format = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat candidate = extractor.getTrackFormat(i);
            String mime = candidate.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                track = i;
                format = candidate;
                break;
            }
        }
        if (track < 0 || format == null) {
            extractor.release();
            throw new IOException("no audio");
        }
        extractor.selectTrack(track);
        int rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
        MediaCodec codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
        codec.configure(format, null, null, 0);
        codec.start();
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inDone = false;
        boolean outDone = false;
        while (!outDone) {
            if (!inDone) {
                int in = codec.dequeueInputBuffer(10_000);
                if (in >= 0) {
                    ByteBuffer buf = codec.getInputBuffer(in);
                    int size = extractor.readSampleData(buf, 0);
                    if (size < 0) {
                        codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inDone = true;
                    } else {
                        codec.queueInputBuffer(in, 0, size, extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                }
            }
            int out = codec.dequeueOutputBuffer(info, 10_000);
            if (out >= 0) {
                ByteBuffer buf = codec.getOutputBuffer(out);
                if (buf != null && info.size > 0) {
                    byte[] chunk = new byte[info.size];
                    buf.position(info.offset);
                    buf.limit(info.offset + info.size);
                    buf.get(chunk);
                    pcm.write(chunk);
                }
                codec.releaseOutputBuffer(out, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    outDone = true;
                }
            } else if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat output = codec.getOutputFormat();
                rate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                if (output.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                }
            }
        }
        codec.stop();
        codec.release();
        extractor.release();
        return mono16k(pcm.toByteArray(), channels, rate);
    }

    /** 16-bit little-endian PCM to 16 kHz mono floats in -1..1. */
    static float[] mono16k(byte[] le, int channels, int rate) {
        if (channels < 1) {
            channels = 1;
        }
        if (rate < 1) {
            rate = 16000;
        }
        int frames = le.length / 2 / channels;
        float[] mono = new float[frames];
        for (int i = 0; i < frames; i++) {
            int sum = 0;
            for (int c = 0; c < channels; c++) {
                int off = (i * channels + c) * 2;
                int sample = (le[off] & 0xff) | (le[off + 1] << 8);
                sum += (short) sample;
            }
            mono[i] = (sum / (float) channels) / 32768f;
        }
        if (rate == 16000) {
            return mono;
        }
        int outFrames = (int) ((long) mono.length * 16000 / rate);
        float[] out = new float[Math.max(outFrames, 0)];
        for (int i = 0; i < out.length; i++) {
            double src = i * (rate / 16000.0);
            int i0 = (int) src;
            if (i0 >= mono.length) {
                i0 = mono.length - 1;
            }
            int i1 = Math.min(i0 + 1, mono.length - 1);
            float frac = (float) (src - i0);
            out[i] = mono[i0] * (1f - frac) + mono[i1] * frac;
        }
        return out;
    }
}
