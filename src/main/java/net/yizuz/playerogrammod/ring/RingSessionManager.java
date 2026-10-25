package net.yizuz.playerogrammod.ring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RingSessionManager {
    public interface Listener {
        void onSessionChanged(UUID playerId, UUID sessionToken, boolean active);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(RingSessionManager.class);
    private static final RingSessionManager INSTANCE = new RingSessionManager();

    private final Map<UUID, UUID> sessions = new ConcurrentHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public static RingSessionManager get() {
        return INSTANCE;
    }

    public UUID activate(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        UUID token = UUID.randomUUID();
        UUID existing = sessions.putIfAbsent(playerId, token);
        if (existing != null) {
            return null;
        }
        notifyChanged(playerId, token, true);
        return token;
    }

    public boolean deactivate(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        UUID removed = sessions.remove(playerId);
        if (removed == null) {
            return false;
        }
        notifyChanged(playerId, removed, false);
        return true;
    }

    public boolean isActive(UUID playerId) {
        return playerId != null && sessions.containsKey(playerId);
    }

    public UUID token(UUID playerId) {
        return playerId == null ? null : sessions.get(playerId);
    }

    public boolean isCurrent(UUID playerId, UUID token) {
        return playerId != null && token != null && token.equals(sessions.get(playerId));
    }

    public int size() {
        return sessions.size();
    }

    public List<UUID> activePlayers() {
        return List.copyOf(sessions.keySet());
    }

    public void clear() {
        for (Map.Entry<UUID, UUID> entry : sessions.entrySet()) {
            if (sessions.remove(entry.getKey(), entry.getValue())) {
                notifyChanged(entry.getKey(), entry.getValue(), false);
            }
        }
    }

    public void addListener(Listener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyChanged(UUID playerId, UUID token, boolean active) {
        for (Listener listener : listeners) {
            try {
                listener.onSessionChanged(playerId, token, active);
            } catch (RuntimeException e) {
                LOGGER.warn("Ring session listener failed.", e);
            }
        }
    }
}
