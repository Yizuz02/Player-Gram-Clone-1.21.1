package net.yizuz.playerogrammod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.yizuz.playerogrammod.PlayerOGramMod;

import java.util.UUID;

/**
 * Broadcasts a player's ring flight state so every client can render the aura
 * and play the flight animation for that player.
 */
public record RingFlightStatePayload(UUID playerId, boolean active) implements CustomPacketPayload {
    public static final Type<RingFlightStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlayerOGramMod.MOD_ID, "ring_flight_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RingFlightStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUUID(payload.playerId());
                        buf.writeBoolean(payload.active());
                    },
                    buf -> new RingFlightStatePayload(buf.readUUID(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
