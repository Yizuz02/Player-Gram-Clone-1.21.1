package net.yizuz.playerogrammod.hologram;

import net.yizuz.playerogrammod.Config;

/**
 * Immutable resolved hologram settings. Kept free of Minecraft classes so the
 * fallback rules and follow thresholds can be unit tested.
 */
public record HologramSettings(
        boolean enabled,
        String skinUsername,
        String displayName,
        double followStartDistance,
        double followStopDistance,
        double followSpeed,
        boolean nameplate
) {
    public HologramSettings {
        skinUsername = skinUsername == null ? "" : skinUsername.trim();
        displayName = displayName == null ? "" : displayName.trim();
        followStartDistance = Math.max(1.0, followStartDistance);
        followStopDistance = Math.max(0.5, Math.min(followStopDistance, followStartDistance - 0.25));
        followSpeed = Math.max(0.1, followSpeed);
    }

    public static HologramSettings defaults() {
        return new HologramSettings(true, "", "", 4.0, 2.25, 1.0, true);
    }

    public static HologramSettings current() {
        try {
            return new HologramSettings(
                    Config.HOLOGRAM_ENABLED.get(),
                    Config.HOLOGRAM_SKIN_USERNAME.get(),
                    Config.HOLOGRAM_DISPLAY_NAME.get(),
                    Config.HOLOGRAM_FOLLOW_START_DISTANCE.get(),
                    Config.HOLOGRAM_FOLLOW_STOP_DISTANCE.get(),
                    Config.HOLOGRAM_FOLLOW_SPEED.get(),
                    Config.HOLOGRAM_NAMEPLATE.get());
        } catch (RuntimeException e) {
            return defaults();
        }
    }

    public String resolveSkinUsername(String ownerName) {
        if (!skinUsername.isBlank()) {
            return skinUsername;
        }
        return ownerName == null ? "" : ownerName;
    }

    public String resolveDisplayName(String resolvedSkinUsername) {
        if (!displayName.isBlank()) {
            return displayName;
        }
        return resolvedSkinUsername == null ? "" : resolvedSkinUsername;
    }

    public static boolean shouldMove(double distanceSq, double startDistance) {
        return distanceSq > startDistance * startDistance;
    }

    public static boolean shouldStop(double distanceSq, double stopDistance) {
        return distanceSq <= stopDistance * stopDistance;
    }
}
