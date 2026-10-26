package net.yizuz.playerogrammod.ring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingHoldStateTest {
    @AfterEach
    void clear() {
        RingHoldState.clear();
    }

    @Test
    void onlyTheMatchingRingReceivesProgress() {
        UUID ring = UUID.randomUUID();
        RingHoldState.update(ring, 0.5f, true);

        assertTrue(RingHoldState.isActiveFor(ring));
        assertEquals(0.5f, RingHoldState.progressFor(ring), 0.0001f);
        assertFalse(RingHoldState.isActiveFor(UUID.randomUUID()));
        assertEquals(0.0f, RingHoldState.progressFor(UUID.randomUUID()), 0.0001f);
    }

    @Test
    void progressIsClampedAndClearRemovesIt() {
        UUID ring = UUID.randomUUID();
        RingHoldState.update(ring, 2.0f, true);
        assertEquals(1.0f, RingHoldState.progressFor(ring), 0.0001f);

        RingHoldState.update(ring, -2.0f, true);
        assertEquals(0.0f, RingHoldState.progressFor(ring), 0.0001f);

        RingHoldState.clear();
        assertFalse(RingHoldState.isActiveFor(ring));
    }

    @Test
    void inactiveStateNeverReportsProgress() {
        UUID ring = UUID.randomUUID();
        RingHoldState.update(ring, 0.7f, false);
        assertFalse(RingHoldState.isActiveFor(ring));
        assertEquals(0.0f, RingHoldState.progressFor(ring), 0.0001f);
    }
}
