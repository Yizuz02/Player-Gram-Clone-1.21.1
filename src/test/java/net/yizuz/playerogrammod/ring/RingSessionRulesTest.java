package net.yizuz.playerogrammod.ring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingSessionRulesTest {
    @Test
    void activationRequiresEnabledAndReady() {
        assertTrue(RingSessionRules.canActivate(true, true));
        assertFalse(RingSessionRules.canActivate(true, false));
        assertFalse(RingSessionRules.canActivate(false, true));
    }

    @Test
    void routingRequiresActiveSessionAndHeldRing() {
        assertTrue(RingSessionRules.shouldRoute(true, true, true));
        assertFalse(RingSessionRules.shouldRoute(true, false, true));
        assertFalse(RingSessionRules.shouldRoute(true, true, false));
        assertFalse(RingSessionRules.shouldRoute(false, true, true));
    }

    @Test
    void activeSessionWithoutRingShouldDeactivate() {
        assertTrue(RingSessionRules.shouldDeactivate(true, false));
        assertFalse(RingSessionRules.shouldDeactivate(true, true));
        assertFalse(RingSessionRules.shouldDeactivate(false, false));
    }
}
