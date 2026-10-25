package net.yizuz.playerogrammod.hologram;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owner-to-hologram mapping. Pure Java so duplicate-prevention and cleanup rules
 * can be unit tested without a Minecraft server.
 */
public final class HologramRegistry {
    public record Entry(UUID owner, UUID token, UUID entityId) {
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public Entry put(UUID owner, UUID token, UUID entityId) {
        if (owner == null || token == null || entityId == null) {
            throw new IllegalArgumentException("owner, token, and entityId are required");
        }
        return entries.put(owner, new Entry(owner, token, entityId));
    }

    public Entry get(UUID owner) {
        return owner == null ? null : entries.get(owner);
    }

    public boolean contains(UUID owner) {
        return owner != null && entries.containsKey(owner);
    }

    public Entry remove(UUID owner) {
        return owner == null ? null : entries.remove(owner);
    }

    public boolean isCurrent(UUID owner, UUID token) {
        Entry entry = get(owner);
        return entry != null && entry.token().equals(token);
    }

    public Collection<Entry> entries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public Collection<UUID> owners() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }
}
