package net.yizuz.playerogrammod.ring;

import java.util.UUID;

/**
 * Common hand-off for the client-side hold progress so the item's tooltip can
 * render the progress bar without loading client classes on a dedicated server.
 */
public final class RingHoldState {
    private static volatile UUID ringId;
    private static volatile float progress;
    private static volatile boolean active;

    public static void update(UUID id, float value, boolean isActive) {
        ringId = id;
        progress = Math.max(0.0f, Math.min(1.0f, value));
        active = isActive && id != null;
    }

    public static void clear() {
        ringId = null;
        progress = 0.0f;
        active = false;
    }

    public static boolean isActiveFor(UUID id) {
        return active && ringId != null && ringId.equals(id);
    }

    public static float progressFor(UUID id) {
        return isActiveFor(id) ? progress : 0.0f;
    }

    private RingHoldState() {
    }
}
