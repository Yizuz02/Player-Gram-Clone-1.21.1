package net.yizuz.playerogrammod.ring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RingSessionManager {
    public record Session(UUID token, UUID ringId, String systemPrompt, String displayName) {
    }

    public interface Listener {
        void onSessionChanged(UUID playerId, UUID sessionToken, boolean active);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(RingSessionManager.class);
    private static final RingSessionManager INSTANCE = new RingSessionManager();

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public static RingSessionManager get() {
        return INSTANCE;
    }

    public UUID activate(UUID playerId) {
        return activate(playerId, null, null, null);
    }

    public UUID activate(UUID playerId, UUID ringId, String systemPrompt) {
        return activate(playerId, ringId, systemPrompt, null);
    }

    public UUID activate(UUID playerId, UUID ringId, String systemPrompt, String displayName) {
        if (playerId == null) {
            return null;
        }
        UUID token = UUID.randomUUID();
        Session session = new Session(token, ringId, systemPrompt, displayName);
        Session existing = sessions.putIfAbsent(playerId, session);
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
        Session removed = sessions.remove(playerId);
        if (removed == null) {
            return false;
        }
        notifyChanged(playerId, removed.token(), false);
        return true;
    }

    public boolean isActive(UUID playerId) {
        return playerId != null && sessions.containsKey(playerId);
    }

    public Session session(UUID playerId) {
        return playerId == null ? null : sessions.get(playerId);
    }

    public UUID token(UUID playerId) {
        Session session = session(playerId);
        return session == null ? null : session.token();
    }

    public UUID ringId(UUID playerId) {
        Session session = session(playerId);
        return session == null ? null : session.ringId();
    }

    public String prompt(UUID playerId) {
        Session session = session(playerId);
        return session == null ? null : session.systemPrompt();
    }

    public String displayName(UUID playerId) {
        Session session = session(playerId);
        return session == null ? null : session.displayName();
    }

    public boolean updatePrompt(UUID playerId, UUID ringId, String systemPrompt) {
        Session current = session(playerId);
        if (current == null) {
            return false;
        }
        return updateSettings(playerId, ringId, systemPrompt, current.displayName());
    }

    public boolean updateSettings(UUID playerId, UUID ringId, String systemPrompt, String displayName) {
        Session current = session(playerId);
        if (current == null || (ringId != null && !ringId.equals(current.ringId()))) {
            return false;
        }
        return sessions.replace(playerId, current,
                new Session(current.token(), current.ringId(), systemPrompt, displayName));
    }

    public boolean ringIdActiveElsewhere(UUID playerId, UUID ringId) {
        if (ringId == null) {
            return false;
        }
        for (Map.Entry<UUID, Session> entry : sessions.entrySet()) {
            if (!entry.getKey().equals(playerId) && ringId.equals(entry.getValue().ringId())) {
                return true;
            }
        }
        return false;
    }

    public boolean isCurrent(UUID playerId, UUID token) {
        Session session = session(playerId);
        return session != null && token != null && token.equals(session.token());
    }

    public int size() {
        return sessions.size();
    }

    public List<UUID> activePlayers() {
        return List.copyOf(sessions.keySet());
    }

    public void clear() {
        for (Map.Entry<UUID, Session> entry : sessions.entrySet()) {
            if (sessions.remove(entry.getKey(), entry.getValue())) {
                notifyChanged(entry.getKey(), entry.getValue().token(), false);
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
