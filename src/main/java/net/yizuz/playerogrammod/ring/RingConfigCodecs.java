package net.yizuz.playerogrammod.ring;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Minecraft codecs for the ring configuration. The canonical wire/persistence
 * format is the pure JSON produced by {@link RingConfig#toJson()}.
 */
public final class RingConfigCodecs {
    public static final Codec<RingConfig> CODEC =
            Codec.STRING.xmap(RingConfig::fromJson, RingConfig::toJson);

    public static final StreamCodec<ByteBuf, RingConfig> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(RingConfig::fromJson, RingConfig::toJson);

    private RingConfigCodecs() {
    }
}
