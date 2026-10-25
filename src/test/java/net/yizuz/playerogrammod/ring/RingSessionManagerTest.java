package net.yizuz.playerogrammod.ring;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingSessionManagerTest {
    @Test
    void activatesAndDeactivatesPerPlayer() {
        RingSessionManager manager = new RingSessionManager();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        UUID token = manager.activate(first);

        assertNotNull(token);
        assertTrue(manager.isActive(first));
        assertFalse(manager.isActive(second));
        assertEquals(token, manager.token(first));
        assertTrue(manager.isCurrent(first, token));

        assertTrue(manager.deactivate(first));
        assertFalse(manager.isActive(first));
        assertFalse(manager.deactivate(first));
    }

    @Test
    void duplicateActivationIsRejectedAndReactivationGetsANewToken() {
        RingSessionManager manager = new RingSessionManager();
        UUID player = UUID.randomUUID();

        UUID first = manager.activate(player);
        assertNotNull(first);
        assertNull(manager.activate(player));

        manager.deactivate(player);
        UUID second = manager.activate(player);

        assertNotNull(second);
        assertNotEquals(first, second);
        assertFalse(manager.isCurrent(player, first));
        assertTrue(manager.isCurrent(player, second));
    }

    @Test
    void clearRemovesEverySession() {
        RingSessionManager manager = new RingSessionManager();
        manager.activate(UUID.randomUUID());
        manager.activate(UUID.randomUUID());

        manager.clear();

        assertEquals(0, manager.size());
        assertTrue(manager.activePlayers().isEmpty());
    }

    @Test
    void listenersReceiveActivationAndDeactivation() {
        RingSessionManager manager = new RingSessionManager();
        List<String> events = new ArrayList<>();
        manager.addListener((playerId, token, active) ->
                events.add((active ? "on:" : "off:") + playerId + ":" + token));

        UUID player = UUID.randomUUID();
        UUID token = manager.activate(player);
        manager.deactivate(player);

        assertEquals(List.of("on:" + player + ":" + token, "off:" + player + ":" + token), events);
    }
}
