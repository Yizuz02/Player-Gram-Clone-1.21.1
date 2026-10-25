package net.yizuz.playerogrammod.hologram;

/**
 * Tunable hologram effect constants and progress calculations.
 *
 * <p>The shader is authored against the same green palette. Keeping the durations here
 * lets the server's dematerialization countdown and the client's fade agree.</p>
 */
public final class HologramVisuals {
    public static final int MATERIALIZE_TICKS = 12;
    public static final int DEMATERIALIZE_TICKS = 12;

    public static final int GREEN = 0x3CFF88;
    public static final float GREEN_RED = 0.235f;
    public static final float GREEN_GREEN = 1.0f;
    public static final float GREEN_BLUE = 0.533f;

    public static float materializeProgress(int tickCount) {
        return clamp01((tickCount + 1) / (float) MATERIALIZE_TICKS);
    }

    public static float dematerializeProgress(int remainingTicks) {
        return clamp01(remainingTicks / (float) DEMATERIALIZE_TICKS);
    }

    public static float renderProgress(int tickCount, int dematerializeTicks) {
        return dematerializeTicks >= 0
                ? dematerializeProgress(dematerializeTicks)
                : materializeProgress(tickCount);
    }

    public static float clamp01(float value) {
        if (Float.isNaN(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private HologramVisuals() {
    }
}
