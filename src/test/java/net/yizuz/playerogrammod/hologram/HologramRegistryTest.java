package net.yizuz.playerogrammod.hologram;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HologramRegistryTest {
    @Test
    void storesOneHologramPerOwner() {
        HologramRegistry registry = new HologramRegistry();
        UUID owner = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        UUID entity = UUID.randomUUID();

        assertNull(registry.put(owner, token, entity));
        HologramRegistry.Entry entry = registry.get(owner);
        assertEquals(token, entry.token());
        assertEquals(entity, entry.entityId());
        assertTrue(registry.isCurrent(owner, token));
        assertFalse(registry.isCurrent(owner, UUID.randomUUID()));
    }

    @Test
    void replacingAnOwnerReturnsThePreviousEntry() {
        HologramRegistry registry = new HologramRegistry();
        UUID owner = UUID.randomUUID();
        UUID firstToken = UUID.randomUUID();
        UUID secondToken = UUID.randomUUID();
        UUID firstEntity = UUID.randomUUID();
        UUID secondEntity = UUID.randomUUID();

        registry.put(owner, firstToken, firstEntity);
        HologramRegistry.Entry previous = registry.put(owner, secondToken, secondEntity);

        assertEquals(firstEntity, previous.entityId());
        assertEquals(secondEntity, registry.get(owner).entityId());
        assertTrue(registry.isCurrent(owner, secondToken));
        assertFalse(registry.isCurrent(owner, firstToken));
        assertEquals(1, registry.size());
    }

    @Test
    void storesRingIdentityAlongsideTheOwnerAndEntity() {
        HologramRegistry registry = new HologramRegistry();
        UUID owner = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        UUID entity = UUID.randomUUID();
        UUID ring = UUID.randomUUID();

        registry.put(owner, token, entity, ring);

        assertEquals(ring, registry.get(owner).ringId());
    }

    @Test
    void legacyPutWithoutRingIdentityStaysCompatible() {
        HologramRegistry registry = new HologramRegistry();
        UUID owner = UUID.randomUUID();
        registry.put(owner, UUID.randomUUID(), UUID.randomUUID());
        assertNull(registry.get(owner).ringId());
    }

    @Test
    void multipleOwnersStayIndependent() {
        HologramRegistry registry = new HologramRegistry();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        registry.put(first, token, UUID.randomUUID());
        registry.put(second, token, UUID.randomUUID());

        assertEquals(2, registry.size());
        assertTrue(registry.isCurrent(first, token));
        assertTrue(registry.isCurrent(second, token));
        registry.remove(first);
        assertFalse(registry.contains(first));
        assertTrue(registry.contains(second));
    }

    @Test
    void removalAndClearAreIdempotent() {
        HologramRegistry registry = new HologramRegistry();
        UUID owner = UUID.randomUUID();
        registry.put(owner, UUID.randomUUID(), UUID.randomUUID());
        registry.remove(owner);
        assertNull(registry.remove(owner));
        registry.clear();
        assertEquals(0, registry.size());
        assertTrue(registry.entries().isEmpty());
    }

    @Test
    void nullInputsAreRejected() {
        HologramRegistry registry = new HologramRegistry();
        assertThrows(IllegalArgumentException.class,
                () -> registry.put(null, UUID.randomUUID(), UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,
                () -> registry.put(UUID.randomUUID(), null, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,
                () -> registry.put(UUID.randomUUID(), UUID.randomUUID(), null));
    }

    @Test
    void entriesAreReadOnlyViews() {
        HologramRegistry registry = new HologramRegistry();
        UUID owner = UUID.randomUUID();
        registry.put(owner, UUID.randomUUID(), UUID.randomUUID());
        assertThrows(UnsupportedOperationException.class,
                () -> registry.entries().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> registry.owners().clear());
        assertSame(owner, registry.entries().iterator().next().owner());
    }
}
