package app.cuepipe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BurnedTest {
    @Test
    public void aMisreadTickerIsTheSameLine() {
        String first = "RU PURSUING MULTIPOLARITY BEIJING RAILWAY TRAFFIC "
                + "PARTIALLY REOPENED AFTER ATTACK ON BRIDGE";
        String noisy = "CIBU RU PURSUING MULTIPOLARITY BEIJING RAILWAY TRAFFIC "
                + "PARTIALLY REOPENED AFTER ATTACK ON BRIDGE";
        String shifted = "PHOGT RU PURSUING MULTIPOLARITY RAILWAY TRAFFIC "
                + "PARTIALLY REOPENED AFTER ATTACK ON BRIDGE";
        assertTrue(Burned.sameLine(first, noisy));
        assertTrue(Burned.sameLine(first, shifted));
        assertFalse(Burned.sameLine("hello elephant", "second line"));
        assertFalse(Burned.sameLine("你好世界", "第二行字幕"));
    }
}
