package net.yizuz.playerogrammod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.yizuz.playerogrammod.PlayerOGramMod;

import java.util.UUID;

/**
 * Broadcasts whether a flying player is in creative-style hover mode so every
 * client can render the upright hover pose and outline.
 */
public record RingFlightHoverStatePayload(UUID playerId, boolean hover) implements CustomPacketPayload {
    public static final Type<RingFlightHoverStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlayerOGramMod.MOD_ID, "ring_flight_hover_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RingFlightHoverStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUUID(payload.playerId());
                        buf.writeBoolean(payload.hover());
                    },
                    buf -> new RingFlightHoverStatePayload(buf.readUUID(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
