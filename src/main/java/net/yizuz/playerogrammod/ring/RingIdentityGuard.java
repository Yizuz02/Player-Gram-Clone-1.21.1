package net.yizuz.playerogrammod.ring;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Pure helpers for keeping copied rings distinct.
 */
public final class RingIdentityGuard {
    public static boolean shouldReassign(int currentSlot, int duplicateSlot) {
        return duplicateSlot < currentSlot;
    }

    /**
     * Returns IDs with duplicates replaced by generated IDs. Null/zero IDs are
     * also replaced. The first occurrence of an ID keeps it.
     */
    public static List<UUID> assignUnique(List<UUID> ids, Supplier<UUID> generator) {
        List<UUID> result = new ArrayList<>(ids.size());
        Set<UUID> seen = new HashSet<>();
        for (UUID id : ids) {
            UUID candidate = id;
            if (candidate == null || RingConfig.UNASSIGNED.equals(candidate) || !seen.add(candidate)) {
                candidate = generator.get();
                while (candidate == null || RingConfig.UNASSIGNED.equals(candidate) || !seen.add(candidate)) {
                    candidate = generator.get();
                }
            }
            result.add(candidate);
        }
        return result;
    }

    private RingIdentityGuard() {
    }
}
