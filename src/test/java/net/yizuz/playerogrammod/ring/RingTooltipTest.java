package net.yizuz.playerogrammod.ring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingTooltipTest {
    @Test
    void emptyAndFilledSegmentsUseCreateStyleGlyphs() {
        assertEquals(" " + "\u2592".repeat(20) + " ", RingTooltip.progressBar(20, 0));
        assertEquals(" " + "\u2588".repeat(7) + "\u2592".repeat(13) + " ",
                RingTooltip.progressBar(20, 7));
        assertEquals(" " + "\u2588".repeat(20) + " ", RingTooltip.progressBar(20, 20));
    }

    @Test
    void lengthsAreClamped() {
        assertEquals(" " + "\u2588".repeat(5) + " ", RingTooltip.progressBar(5, 50));
        assertTrue(RingTooltip.progressBar(5, -1).startsWith(" "));
        assertEquals("  ", RingTooltip.progressBar(0, 3));
    }
}
