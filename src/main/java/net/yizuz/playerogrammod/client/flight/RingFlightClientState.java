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

    public static void setActive(UUID playerId, boolean active) {
        if (playerId == null) {
            return;
        }
        if (active) {
            ACTIVE.add(playerId);
        } else {
            ACTIVE.remove(playerId);
        }
    }

    public static boolean isActive(UUID playerId) {
        return playerId != null && ACTIVE.contains(playerId);
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private RingFlightClientState() {
    }
}
