package net.yizuz.playerogrammod.client.flight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.framebuffer.FramebufferAttachmentDefinition;
import foundry.veil.api.client.render.post.PostPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.yizuz.playerogrammod.PlayerOGramMod;
import org.lwjgl.opengl.GL11C;

/**
 * Veil post-process outline. Flying players are rendered into a screen-sized
 * mask buffer, then a post pipeline edge-detects that mask and composites a
 * green outline over the scene.
 */
public final class RingFlightPostOutline {
    public static final ResourceLocation MASK_ID =
            ResourceLocation.fromNamespaceAndPath(PlayerOGramMod.MOD_ID, "flight_mask");
    private static final ResourceLocation PIPELINE_ID =
            ResourceLocation.fromNamespaceAndPath(PlayerOGramMod.MOD_ID, "flight_outline");
    private static final float MODEL_SCALE = 0.9375F;
    private static final double MODEL_Y_OFFSET = -1.501D;

    private static AdvancedFbo mask;
    private static int maskWidth;
    private static int maskHeight;
    private static int maskDepthTexture;
    private static boolean failed;

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            ensureMask();
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            if (failed || mask == null || !RingFlightClientState.hasAny()) {
                return;
            }
            PostPipeline pipeline = getPipeline();
            if (pipeline == null) {
                return;
            }
            try {
                VeilRenderSystem.renderer().getPostProcessingManager().runPipeline(pipeline);
            } catch (RuntimeException e) {
                failed = true;
            }
        }
    }

    public static void renderMask(RenderPlayerEvent.Post event, AbstractClientPlayer player) {
        if (failed || mask == null) {
            return;
        }
        PlayerRenderer renderer = event.getRenderer();
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        ResourceLocation texture = renderer.getTextureLocation(player);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        float bodyYaw = Mth.rotLerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot);
        float entityScale = player.getScale();

        RenderType maskType = RingFlightRenderTypes.mask(texture);
        poseStack.pushPose();
        applyModelTransform(poseStack, bodyYaw, entityScale);
        model.renderToBuffer(poseStack, buffers.getBuffer(maskType), event.getPackedLight(),
                OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        poseStack.popPose();
        buffers.endBatch(maskType);
    }

    private static void applyModelTransform(PoseStack poseStack, float bodyYaw, float entityScale) {
        poseStack.scale(entityScale, entityScale, entityScale);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        poseStack.translate(0.0D, MODEL_Y_OFFSET, 0.0D);
    }

    private static void ensureMask() {
        try {
            AdvancedFbo main = AdvancedFbo.getMainFramebuffer();
            if (main == null || main.getDepthTextureAttachment() == null) {
                failed = true;
                return;
            }
            int width = main.getWidth();
            int height = main.getHeight();
            int depthTexture = main.getDepthTextureAttachment().getId();
            if (mask == null || width != maskWidth || height != maskHeight
                    || depthTexture != maskDepthTexture) {
                freeMask();
                mask = AdvancedFbo.withSize(width, height)
                        .setFormat(FramebufferAttachmentDefinition.Format.R8)
                        .addColorTextureBuffer()
                        .setDepthTextureWrapper(depthTexture)
                        .setDebugLabel("Player-O-Gram Flight Mask")
                        .build(true);
                maskWidth = width;
                maskHeight = height;
                maskDepthTexture = depthTexture;
                VeilRenderSystem.renderer().getFramebufferManager().setFramebuffer(MASK_ID, mask);
            }
            var manager = VeilRenderSystem.renderer().getFramebufferManager();
            if (manager.getFramebuffer(MASK_ID) != mask) {
                manager.setFramebuffer(MASK_ID, mask);
            }
            mask.clear(GL11C.GL_COLOR_BUFFER_BIT);
            failed = false;
        } catch (RuntimeException e) {
            failed = true;
            freeMask();
        }
    }

    private static PostPipeline getPipeline() {
        try {
            var renderer = VeilRenderSystem.renderer();
            return renderer == null ? null
                    : renderer.getPostProcessingManager().getPipeline(PIPELINE_ID);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void freeMask() {
        if (mask != null) {
            try {
                VeilRenderSystem.renderer().getFramebufferManager().removeFramebuffer(MASK_ID);
            } catch (RuntimeException ignored) {
            }
            mask.free();
            mask = null;
        }
        maskWidth = 0;
        maskHeight = 0;
        maskDepthTexture = 0;
    }

    private RingFlightPostOutline() {
    }
}
