package app.cuepipe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class PcmTest {
    @Test
    public void sixteenKhzMonoStaysTheSameLength() {
        byte[] le = new byte[]{0, 0, 0, 64}; // 0 and 16384
        float[] out = Pcm.mono16k(le, 1, 16000);
        assertEquals(2, out.length);
        assertEquals(0f, out[0], 0.001f);
        assertEquals(0.5f, out[1], 0.001f);
    }

    @Test
    public void stereoAveragesToMono() {
        // left 0, right 16384
        byte[] le = new byte[]{0, 0, 0, 64};
        float[] out = Pcm.mono16k(le, 2, 16000);
        assertEquals(1, out.length);
        assertEquals(0.25f, out[0], 0.001f);
    }
}
