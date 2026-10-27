package net.yizuz.playerogrammod.client.flight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Applies the flight body lean and feeds the posed player model into the
 * post-process outline mask.
 */
public final class RingFlightOutline {
    private static final double LEAN_PIVOT_Y = 0.9D;

    private static boolean leanPushed;

    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)
                || !RingFlightClientState.isActive(player.getUUID())) {
            return;
        }

        float lean = RingFlightPose.leanDegrees(player);
        if (Math.abs(lean) < 1.0F) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        float bodyYaw = Mth.rotLerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot);
        poseStack.pushPose();
        poseStack.translate(0.0D, LEAN_PIVOT_Y, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-lean));
        poseStack.mulPose(Axis.YP.rotationDegrees(-(180.0F - bodyYaw)));
        poseStack.translate(0.0D, -LEAN_PIVOT_Y, 0.0D);
        leanPushed = true;
    }

    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (event.getEntity() instanceof AbstractClientPlayer player
                && RingFlightClientState.isActive(player.getUUID())
                && !player.isInvisible()) {
            RingFlightPostOutline.renderMask(event, player);
        }
        if (leanPushed) {
            event.getPoseStack().popPose();
            leanPushed = false;
        }
    }

    public static void reset() {
        leanPushed = false;
    }

    private RingFlightOutline() {
    }
}
