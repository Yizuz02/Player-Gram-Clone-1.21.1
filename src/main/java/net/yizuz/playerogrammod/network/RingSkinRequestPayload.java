package net.yizuz.playerogrammod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.yizuz.playerogrammod.PlayerOGramMod;
import net.yizuz.playerogrammod.ring.RingValidation;

import java.util.UUID;

public record RingSkinRequestPayload(UUID requestId, String username) implements CustomPacketPayload {
    public static final Type<RingSkinRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlayerOGramMod.MOD_ID, "ring_skin_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RingSkinRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUUID(payload.requestId());
                        buf.writeUtf(payload.username(), RingValidation.MAX_SKIN_USERNAME);
                    },
                    buf -> new RingSkinRequestPayload(
                            buf.readUUID(), buf.readUtf(RingValidation.MAX_SKIN_USERNAME)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
