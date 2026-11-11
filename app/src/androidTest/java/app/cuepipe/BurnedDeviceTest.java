package app.cuepipe;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class BurnedDeviceTest {
    @Test
    public void readsTheBottomLine() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context test = InstrumentationRegistry.getInstrumentation().getContext();
        File out = new File(target.getCacheDir(), "burned.mp4");
        try (InputStream in = test.getAssets().open("burned.mp4");
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                fos.write(buf, 0, n);
            }
        }
        Cues.Transcript transcript = Burned.read(out.getAbsolutePath(), null);
        String body = transcript.body.toLowerCase();
        assertTrue(body, body.contains("hello") && body.contains("elephant"));
        assertTrue(body, body.contains("second"));
    }
}
