package net.yizuz.playerogrammod.ring;

/**
 * Small tooltip helpers. The progress bar uses the same filled/empty block glyphs
 * as Create's tooltip helper so it reads naturally in inventory lore.
 */
public final class RingTooltip {
    public static String progressBar(int length, int filledLength) {
        int lengthSafe = Math.max(0, length);
        int filled = Math.max(0, Math.min(filledLength, lengthSafe));
        StringBuilder bar = new StringBuilder(" ");
        for (int i = 0; i < filled; i++) {
            bar.append('\u2588');
        }
        for (int i = filled; i < lengthSafe; i++) {
            bar.append('\u2592');
        }
        return bar.append(' ').toString();
    }

    private RingTooltip() {
    }
}
