package net.yizuz.playerogrammod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.yizuz.playerogrammod.entity.HologramEntity;

/**
 * Reuses the vanilla player model/animations and applies the hologram-only Veil
 * render type. The slim/wide model is selected from the resolved PlayerSkin.
 */
public final class HologramRenderer extends LivingEntityRenderer<HologramEntity, PlayerModel<HologramEntity>> {
    private final PlayerModel<HologramEntity> wide;
    private final PlayerModel<HologramEntity> slim;

    public HologramRenderer(EntityRendererProvider.Context context) {
        super(context, new HologramPlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wide = this.getModel();
        this.slim = new HologramPlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(HologramEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        PlayerSkin skin = HologramSkins.skin(entity);
        this.model = skin.model() == PlayerSkin.Model.SLIM ? this.slim : this.wide;
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(HologramEntity entity) {
        return HologramSkins.skin(entity).texture();
    }

    @Override
    protected RenderType getRenderType(HologramEntity entity, boolean bodyVisible, boolean translucent,
                                       boolean glowing) {
        ResourceLocation texture = this.getTextureLocation(entity);
        if (!bodyVisible) {
            return glowing ? RenderType.outline(texture) : null;
        }
        return HologramRenderTypes.get(texture);
    }
}
