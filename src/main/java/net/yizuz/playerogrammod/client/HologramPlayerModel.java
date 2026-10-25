package net.yizuz.playerogrammod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.yizuz.playerogrammod.entity.HologramEntity;
import net.yizuz.playerogrammod.hologram.HologramVisuals;

/**
 * The vanilla player model with the hologram's materialization alpha encoded in
 * the per-vertex color. This keeps batched holograms independent without a global
 * shader uniform.
 */
final class HologramPlayerModel extends PlayerModel<HologramEntity> {
    private float progress = 1.0f;

    HologramPlayerModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(HologramEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        this.progress = HologramVisuals.renderProgress(entity.tickCount, entity.getDematerializeTicks());
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight,
                               int packedOverlay, int color) {
        int alpha = Mth.clamp((int) (this.progress * 255.0f), 0, 255);
        super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay,
                (color & 0x00FFFFFF) | (alpha << 24));
    }
}
