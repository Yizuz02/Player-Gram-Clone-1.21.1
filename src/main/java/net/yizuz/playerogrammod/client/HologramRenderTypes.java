package net.yizuz.playerogrammod.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import foundry.veil.api.client.render.VeilRenderBridge;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.yizuz.playerogrammod.PlayerOGramMod;

import java.util.function.BiFunction;

/**
 * A hologram-only render type using Veil's shader shard. No vanilla render type or
 * entity shader is replaced; only the hologram renderer asks for this type.
 */
final class HologramRenderTypes extends RenderType {
    private static final ShaderStateShard HOLOGRAM_SHADER = VeilRenderBridge.shaderState(
            ResourceLocation.fromNamespaceAndPath(PlayerOGramMod.MOD_ID, "hologram"));

    private static final BiFunction<ResourceLocation, Boolean, RenderType> HOLOGRAM = Util.memoize(
            (texture, outline) -> {
                CompositeState state = CompositeState.builder()
                        .setShaderState(HOLOGRAM_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .createCompositeState(outline);
                return create("playerogram_hologram",
                        DefaultVertexFormat.NEW_ENTITY,
                        VertexFormat.Mode.QUADS,
                        TRANSIENT_BUFFER_SIZE,
                        true,
                        true,
                        state);
            });

    static RenderType get(ResourceLocation texture) {
        return HOLOGRAM.apply(texture, false);
    }

    private HologramRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                                boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState,
                                Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }
}
