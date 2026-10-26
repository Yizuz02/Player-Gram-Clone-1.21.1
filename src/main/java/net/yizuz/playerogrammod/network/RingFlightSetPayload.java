package net.yizuz.playerogrammod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.yizuz.playerogrammod.PlayerOGramMod;

/**
 * Client request to start or stop flying with the held Lantern Ring. The server
 * validates that the ring is still in hand before changing state.
 */
public record RingFlightSetPayload(boolean active) implements CustomPacketPayload {
    public static final Type<RingFlightSetPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlayerOGramMod.MOD_ID, "ring_flight_set"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RingFlightSetPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBoolean(payload.active()),
                    buf -> new RingFlightSetPayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
