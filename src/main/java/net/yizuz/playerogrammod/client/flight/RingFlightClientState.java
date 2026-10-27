package net.yizuz.playerogrammod.client.flight;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client view of which players are currently ring-flying. Populated from the
 * server broadcast so the aura and animation are visible to everyone.
 */
public final class RingFlightClientState {
    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> HOVER = ConcurrentHashMap.newKeySet();

    public static void setActive(UUID playerId, boolean active) {
        if (playerId == null) {
            return;
        }
        if (active) {
            ACTIVE.add(playerId);
        } else {
            ACTIVE.remove(playerId);
            HOVER.remove(playerId);
        }
    }

    public static boolean isActive(UUID playerId) {
        return playerId != null && ACTIVE.contains(playerId);
    }

    public static void setHovering(UUID playerId, boolean hover) {
        if (playerId == null) {
            return;
        }
        if (hover) {
            HOVER.add(playerId);
        } else {
            HOVER.remove(playerId);
        }
    }

    public static boolean isHovering(UUID playerId) {
        return playerId != null && HOVER.contains(playerId);
    }

    public static boolean hasAny() {
        return !ACTIVE.isEmpty();
    }

    public static void clear() {
        ACTIVE.clear();
        HOVER.clear();
    }

    private RingFlightClientState() {
    }
}
