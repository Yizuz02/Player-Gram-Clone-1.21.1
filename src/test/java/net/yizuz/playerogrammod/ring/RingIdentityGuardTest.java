package net.yizuz.playerogrammod.ring;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingIdentityGuardTest {
    @Test
    void earlierInventorySlotKeepsTheIdentity() {
        assertTrue(RingIdentityGuard.shouldReassign(5, 3));
        assertFalse(RingIdentityGuard.shouldReassign(3, 5));
        assertFalse(RingIdentityGuard.shouldReassign(3, 3));
    }

    @Test
    void duplicateAndMissingIdsGetFreshIdentities() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        UUID e = UUID.randomUUID();
        Deque<UUID> generated = new ArrayDeque<>(List.of(b, c, d, e));

        List<UUID> result = RingIdentityGuard.assignUnique(
                Arrays.asList(a, a, null, RingConfig.UNASSIGNED, a),
                generated::removeFirst);

        assertEquals(a, result.get(0));
        assertEquals(b, result.get(1));
        assertEquals(c, result.get(2));
        assertEquals(d, result.get(3));
        assertEquals(e, result.get(4));
        assertEquals(5, result.stream().distinct().count());
    }

    @Test
    void emptyListStaysEmpty() {
        assertTrue(RingIdentityGuard.assignUnique(List.of(), UUID::randomUUID).isEmpty());
    }
}
