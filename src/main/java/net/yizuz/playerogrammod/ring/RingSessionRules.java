package net.yizuz.playerogrammod.ring;

public final class RingSessionRules {
    public static boolean canActivate(boolean aiEnabled, boolean runtimeReady) {
        return aiEnabled && runtimeReady;
    }

    public static boolean shouldRoute(boolean aiEnabled, boolean active, boolean holdingRing) {
        return aiEnabled && active && holdingRing;
    }

    public static boolean shouldDeactivate(boolean active, boolean holdingRing) {
        return active && !holdingRing;
    }

    private RingSessionRules() {
    }
}
