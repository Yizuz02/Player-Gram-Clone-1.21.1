package net.yizuz.playerogrammod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.yizuz.playerogrammod.PlayerOGramMod;

/**
 * Client request to switch the active ring flight between momentum flight and
 * creative-style hover.
 */
public record RingFlightHoverSetPayload(boolean hover) implements CustomPacketPayload {
    public static final Type<RingFlightHoverSetPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlayerOGramMod.MOD_ID, "ring_flight_hover_set"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RingFlightHoverSetPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBoolean(payload.hover()),
                    buf -> new RingFlightHoverSetPayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
