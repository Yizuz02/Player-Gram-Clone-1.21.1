package net.yizuz.playerogrammod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.yizuz.playerogrammod.PlayerOGramMod;
import net.yizuz.playerogrammod.ring.RingValidation;

import java.util.UUID;

public record RingConfigSavePayload(
        UUID ringId,
        String displayName,
        String skinUsername,
        String systemPrompt,
        boolean customPrompt,
        String personalityJson
) implements CustomPacketPayload {
    public static final int MAX_PERSONALITY_JSON = 16000;
    public static final Type<RingConfigSavePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PlayerOGramMod.MOD_ID, "ring_config_save"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RingConfigSavePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUUID(payload.ringId());
                        buf.writeUtf(payload.displayName(), RingValidation.MAX_DISPLAY_NAME);
                        buf.writeUtf(payload.skinUsername(), RingValidation.MAX_SKIN_USERNAME);
                        buf.writeUtf(payload.systemPrompt(), RingValidation.MAX_PROMPT);
                        buf.writeBoolean(payload.customPrompt());
                        buf.writeUtf(payload.personalityJson(), MAX_PERSONALITY_JSON);
                    },
                    buf -> new RingConfigSavePayload(
                            buf.readUUID(),
                            buf.readUtf(RingValidation.MAX_DISPLAY_NAME),
                            buf.readUtf(RingValidation.MAX_SKIN_USERNAME),
                            buf.readUtf(RingValidation.MAX_PROMPT),
                            buf.readBoolean(),
                            buf.readUtf(MAX_PERSONALITY_JSON)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
