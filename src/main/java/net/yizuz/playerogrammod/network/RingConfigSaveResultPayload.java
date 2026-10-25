package net.yizuz.playerogrammod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.yizuz.playerogrammod.PlayerOGramMod;

import java.util.UUID;

public record RingConfigSaveResultPayload(UUID ringId, boolean success, String message)
        implements CustomPacketPayload {
    public static final Type<RingConfigSaveResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlayerOGramMod.MOD_ID, "ring_config_save_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RingConfigSaveResultPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUUID(payload.ringId());
                        buf.writeBoolean(payload.success());
                        buf.writeUtf(payload.message(), 512);
                    },
                    buf -> new RingConfigSaveResultPayload(
                            buf.readUUID(), buf.readBoolean(), buf.readUtf(512)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
