package net.yizuz.playerogrammod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.yizuz.playerogrammod.PlayerOGramMod;

import java.util.UUID;

public record RingSkinResponsePayload(
        UUID requestId,
        String username,
        String textureValue,
        String textureSignature
) implements CustomPacketPayload {
    public static final Type<RingSkinResponsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlayerOGramMod.MOD_ID, "ring_skin_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RingSkinResponsePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUUID(payload.requestId());
                        buf.writeUtf(payload.username(), 16);
                        buf.writeUtf(payload.textureValue());
                        buf.writeUtf(payload.textureSignature());
                    },
                    buf -> new RingSkinResponsePayload(
                            buf.readUUID(), buf.readUtf(16), buf.readUtf(), buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
