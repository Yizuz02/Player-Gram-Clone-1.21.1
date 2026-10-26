package net.yizuz.playerogrammod.client.flight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Turns the ring flight visual into a green outline of the player model and
 * leans the whole model into a laying-down flight pose while moving.
 *
 * <p>The outline uses vanilla's glowing-entity outline pass: players in flight
 * are marked as glowing client-side only, and their outline color is overridden
 * to green before their model is rendered.</p>
 */
public final class RingFlightOutline {
    private static final int OUTLINE_R = 30;
    private static final int OUTLINE_G = 255;
    private static final int OUTLINE_B = 90;
    private static final int OUTLINE_A = 255;
    private static final double LEAN_PIVOT_Y = 0.9D;

    private static final Set<UUID> forcedGlow = new HashSet<>();
    private static boolean leanPushed;

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            forcedGlow.clear();
            return;
        }
        for (AbstractClientPlayer player : level.players()) {
            UUID playerId = player.getUUID();
            if (RingFlightClientState.isActive(playerId)) {
                if (!player.isCurrentlyGlowing()) {
                    player.setGlowingTag(true);
                    forcedGlow.add(playerId);
                }
            } else if (forcedGlow.remove(playerId)) {
                player.setGlowingTag(false);
            }
        }
    }

    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)
                || !RingFlightClientState.isActive(player.getUUID())) {
            return;
        }

        if (event.getMultiBufferSource() instanceof OutlineBufferSource outline) {
            outline.setColor(OUTLINE_R, OUTLINE_G, OUTLINE_B, OUTLINE_A);
        }

        float lean = RingFlightPose.leanDegrees(player);
        if (lean < 1.0F) {
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
        if (leanPushed) {
            event.getPoseStack().popPose();
            leanPushed = false;
        }
    }

    public static void reset() {
        forcedGlow.clear();
        leanPushed = false;
    }

    private RingFlightOutline() {
    }
}
