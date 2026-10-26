package net.yizuz.playerogrammod.flight;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.yizuz.playerogrammod.network.RingFlightStatePayload;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative ring flight state. The client owns the movement feel and
 * the server only decides who is allowed to fly, clears gravity, and tells the
 * clients so the aura and animation can be rendered everywhere.
 */
public final class RingFlightManager {
    private static final String NO_GRAVITY_KEY = "playerogrammod_ring_flight_no_gravity";
    private static final int LANDING_GRACE_TICKS = 10;
    private static final RingFlightManager INSTANCE = new RingFlightManager();

    private final Map<UUID, FlightState> states = new ConcurrentHashMap<>();

    public static RingFlightManager get() {
        return INSTANCE;
    }

    public boolean isActive(UUID playerId) {
        return playerId != null && states.containsKey(playerId);
    }

    public List<UUID> activePlayers() {
        return List.copyOf(states.keySet());
    }

    public void request(ServerPlayer player, boolean active) {
        if (player == null) {
            return;
        }
        if (!active) {
            deactivate(player);
            return;
        }
        if (!RingFlightRules.canFly(player)) {
            broadcast(player.getUUID(), false);
            return;
        }
        activate(player);
    }

    private void activate(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (states.containsKey(playerId)) {
            broadcast(playerId, true);
            return;
        }

        FlightState state = new FlightState(player.isNoGravity());
        states.put(playerId, state);
        player.setNoGravity(true);
        player.getPersistentData().putBoolean(NO_GRAVITY_KEY, true);
        player.resetFallDistance();
        if (player.isFallFlying()) {
            player.stopFallFlying();
        }
        broadcast(playerId, true);
    }

    public void deactivate(ServerPlayer player) {
        if (player == null) {
            return;
        }
        FlightState state = states.remove(player.getUUID());
        if (state == null) {
            return;
        }
        if (!player.isRemoved()) {
            player.setNoGravity(state.previousNoGravity);
            player.getPersistentData().remove(NO_GRAVITY_KEY);
        }
        broadcast(player.getUUID(), false);
    }

    private void deactivate(UUID playerId, MinecraftServer server) {
        FlightState state = states.remove(playerId);
        if (state == null) {
            return;
        }
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
        if (player != null && !player.isRemoved()) {
            player.setNoGravity(state.previousNoGravity);
            player.getPersistentData().remove(NO_GRAVITY_KEY);
        }
        broadcast(playerId, false);
    }

    public void tick(MinecraftServer server) {
        for (UUID playerId : List.copyOf(states.keySet())) {
            FlightState state = states.get(playerId);
            if (state == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                deactivate(playerId, server);
                continue;
            }
            if (!RingFlightRules.canFly(player)) {
                deactivate(player);
                continue;
            }

            state.ticks++;
            player.setNoGravity(true);
            player.resetFallDistance();
            if (player.isFallFlying()) {
                player.stopFallFlying();
            }

            if (player.onGround() && state.ticks > LANDING_GRACE_TICKS
                    && player.getDeltaMovement().y <= 0.05) {
                deactivate(player);
            }
        }
    }

    public void resetOnLogin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        FlightState state = states.remove(player.getUUID());
        boolean hadPersistentFlag = player.getPersistentData().getBoolean(NO_GRAVITY_KEY);
        if (hadPersistentFlag) {
            player.setNoGravity(false);
            player.getPersistentData().remove(NO_GRAVITY_KEY);
        }
        if (state != null || hadPersistentFlag) {
            broadcast(player.getUUID(), false);
        }
    }

    public void deactivateAll(MinecraftServer server) {
        for (UUID playerId : List.copyOf(states.keySet())) {
            FlightState state = states.remove(playerId);
            if (state == null) {
                continue;
            }
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
            if (player != null && !player.isRemoved()) {
                player.setNoGravity(state.previousNoGravity);
                player.getPersistentData().remove(NO_GRAVITY_KEY);
            }
        }
    }

    private void broadcast(UUID playerId, boolean active) {
        PacketDistributor.sendToAllPlayers(new RingFlightStatePayload(playerId, active));
    }

    private static final class FlightState {
        private final boolean previousNoGravity;
        private int ticks;

        private FlightState(boolean previousNoGravity) {
            this.previousNoGravity = previousNoGravity;
        }
    }

    private RingFlightManager() {
    }
}
