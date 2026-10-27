package net.yizuz.playerogrammod.client.flight;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import foundry.veil.api.client.render.VeilRenderBridge;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.yizuz.playerogrammod.PlayerOGramMod;

import java.util.HashMap;
import java.util.Map;

/**
 * Render type that writes flying players into the post-process outline mask.
 */
final class RingFlightRenderTypes extends RenderType {
    private static final ShaderStateShard MASK_SHADER = VeilRenderBridge.shaderState(
            ResourceLocation.fromNamespaceAndPath(PlayerOGramMod.MOD_ID, "flight_mask"));

    private static final Map<ResourceLocation, RenderType> MASK = new HashMap<>();

    static RenderType mask(ResourceLocation texture) {
        return MASK.computeIfAbsent(texture, RingFlightRenderTypes::createMask);
    }

    private static RenderType createMask(ResourceLocation texture) {
        var builder = CompositeState.builder()
                .setShaderState(MASK_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(NO_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(NO_LIGHTMAP)
                .setOverlayState(NO_OVERLAY)
                .setDepthTestState(LEQUAL_DEPTH_TEST)
                .setWriteMaskState(COLOR_WRITE)
                .setOutputState(VeilRenderBridge.outputState(RingFlightPostOutline.MASK_ID));
        return create("playerogram_flight_mask", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 256, false, true, builder.createCompositeState(false));
    }

    private RingFlightRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                                  boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState,
                                  Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }
}
