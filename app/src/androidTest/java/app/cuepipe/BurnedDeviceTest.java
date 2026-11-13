package app.cuepipe;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class BurnedDeviceTest {
    @Test
    public void readsTheBottomLine() throws Exception {
        String body = read("burned.mp4").toLowerCase();
        assertTrue(body, body.contains("hello") && body.contains("elephant"));
        assertTrue(body, body.contains("second"));
        assertTrue(body, body.contains("alpha") && body.contains("beta"));
        assertFalse(body, body.contains("not a cue"));
    }

    @Test
    public void readsChineseInTheSameBand() throws Exception {
        String body = read("burned-zh.mp4");
        assertTrue(body, body.contains("你好") && body.contains("世界"));
        assertTrue(body, body.contains("第二"));
        assertFalse(body, body.contains("不要"));
    }

    @Test
    public void ignoresALineAboveTheBand() throws Exception {
        String body = read("burned-high.mp4").toLowerCase();
        assertFalse(body, body.contains("skyline"));
    }

    private static String read(String asset) throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context test = InstrumentationRegistry.getInstrumentation().getContext();
        File out = new File(target.getCacheDir(), asset);
        try (InputStream in = test.getAssets().open(asset);
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                fos.write(buf, 0, n);
            }
        }
        return Burned.read(out.getAbsolutePath(), null).body;
    }
}
