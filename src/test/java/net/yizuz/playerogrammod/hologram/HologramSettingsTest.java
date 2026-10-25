package net.yizuz.playerogrammod.hologram;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HologramSettingsTest {
    private static final double EPSILON = 0.0001;

    @Test
    void followDistancesAndSpeedAreSanitized() {
        HologramSettings settings = new HologramSettings(true, 4.0, 10.0, -2.0, true);
        assertEquals(3.75, settings.followStopDistance(), EPSILON);
        assertEquals(0.1, settings.followSpeed(), EPSILON);
    }

    @Test
    void followThresholdsUseStartAndStopDistances() {
        assertTrue(HologramSettings.shouldMove(4.01 * 4.01 + 0.01, 4.0));
        assertFalse(HologramSettings.shouldMove(3.9 * 3.9, 4.0));
        assertTrue(HologramSettings.shouldStop(2.25 * 2.25, 2.25));
        assertFalse(HologramSettings.shouldStop(2.26 * 2.26, 2.25));
    }

    @Test
    void defaultsAreEnabled() {
        assertTrue(HologramSettings.defaults().enabled());
        assertTrue(HologramSettings.defaults().nameplate());
    }
}
