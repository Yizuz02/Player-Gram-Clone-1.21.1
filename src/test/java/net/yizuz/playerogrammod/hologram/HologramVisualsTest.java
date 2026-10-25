package net.yizuz.playerogrammod.hologram;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HologramVisualsTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void materializationRunsFromFirstTickToFull() {
        assertEquals(1.0f / HologramVisuals.MATERIALIZE_TICKS,
                HologramVisuals.materializeProgress(0), EPSILON);
        assertEquals(1.0f, HologramVisuals.materializeProgress(HologramVisuals.MATERIALIZE_TICKS - 1), EPSILON);
        assertEquals(1.0f, HologramVisuals.materializeProgress(1000), EPSILON);
    }

    @Test
    void dematerializationRunsFromFullToZero() {
        assertEquals(1.0f, HologramVisuals.dematerializeProgress(HologramVisuals.DEMATERIALIZE_TICKS), EPSILON);
        assertEquals(0.0f, HologramVisuals.dematerializeProgress(0), EPSILON);
        assertEquals(0.0f, HologramVisuals.dematerializeProgress(-4), EPSILON);
    }

    @Test
    void renderProgressChoosesTheActivePhase() {
        assertEquals(HologramVisuals.materializeProgress(3),
                HologramVisuals.renderProgress(3, -1), EPSILON);
        assertEquals(HologramVisuals.dematerializeProgress(3),
                HologramVisuals.renderProgress(100, 3), EPSILON);
    }

    @Test
    void clampHandlesNanAndBounds() {
        assertEquals(0.0f, HologramVisuals.clamp01(Float.NaN), EPSILON);
        assertEquals(0.0f, HologramVisuals.clamp01(-1.0f), EPSILON);
        assertEquals(1.0f, HologramVisuals.clamp01(2.0f), EPSILON);
    }
}
