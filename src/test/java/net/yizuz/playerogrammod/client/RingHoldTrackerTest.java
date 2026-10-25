package net.yizuz.playerogrammod.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingHoldTrackerTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void fillsAndOpensAtTheThreshold() {
        RingHoldTracker tracker = new RingHoldTracker();
        UUID ring = UUID.randomUUID();

        assertFalse(tracker.update(ring, true, 1000L));
        assertTrue(tracker.isTracking());
        assertEquals(0.0f, tracker.progress(1000L), EPSILON);
        assertEquals(0.5f, tracker.progress(1500L), EPSILON);
        assertFalse(tracker.update(ring, true, 1999L));
        assertTrue(tracker.update(ring, true, 2000L));
    }

    @Test
    void releaseBeforeThresholdCancels() {
        RingHoldTracker tracker = new RingHoldTracker();
        UUID ring = UUID.randomUUID();

        tracker.update(ring, true, 1000L);
        assertEquals(0.4f, tracker.progress(1400L), EPSILON);
        assertFalse(tracker.update(ring, false, 1400L));
        assertEquals(0.0f, tracker.progress(1400L), EPSILON);
        assertFalse(tracker.isTracking());
        assertNull(tracker.ringId());
    }

    @Test
    void movingToAnotherRingRestartsProgress() {
        RingHoldTracker tracker = new RingHoldTracker();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        tracker.update(first, true, 0L);
        assertEquals(0.8f, tracker.progress(800L), EPSILON);
        assertFalse(tracker.update(second, true, 800L));
        assertEquals(0.0f, tracker.progress(800L), EPSILON);
        assertEquals(second, tracker.ringId());
    }

    @Test
    void shiftClickSuppressesUntilShiftRelease() {
        RingHoldTracker tracker = new RingHoldTracker();
        UUID ring = UUID.randomUUID();

        tracker.update(ring, true, 0L);
        tracker.click(true);
        assertFalse(tracker.update(ring, true, 1500L));
        assertEquals(0.0f, tracker.progress(1500L), EPSILON);
        assertFalse(tracker.update(ring, true, 5000L));

        tracker.update(ring, false, 5000L);
        assertFalse(tracker.update(ring, true, 6000L));
        assertTrue(tracker.update(ring, true, 7000L));
    }

    @Test
    void openingDoesNotRepeatWhileShiftStaysDown() {
        RingHoldTracker tracker = new RingHoldTracker();
        UUID ring = UUID.randomUUID();

        tracker.update(ring, true, 0L);
        assertTrue(tracker.update(ring, true, 1000L));
        tracker.opened();

        assertFalse(tracker.update(ring, true, 1100L));
        assertFalse(tracker.update(ring, true, 5000L));
        tracker.update(ring, false, 5000L);
        assertFalse(tracker.update(ring, true, 6000L));
        assertTrue(tracker.update(ring, true, 7000L));
    }

    @Test
    void cancelResetsProgressWithoutClearingAnOpenSuppression() {
        RingHoldTracker tracker = new RingHoldTracker();
        UUID ring = UUID.randomUUID();

        tracker.update(ring, true, 0L);
        tracker.opened();
        tracker.cancel();
        assertFalse(tracker.update(ring, true, 1500L));
        tracker.update(ring, false, 1600L);
        assertFalse(tracker.update(ring, true, 2000L));
        assertTrue(tracker.update(ring, true, 3000L));
    }

    @Test
    void progressIsClamped() {
        RingHoldTracker tracker = new RingHoldTracker();
        tracker.update(UUID.randomUUID(), true, 0L);
        assertEquals(1.0f, tracker.progress(10_000L), EPSILON);
        assertEquals(0.0f, tracker.progress(-100L), EPSILON);
    }
}
