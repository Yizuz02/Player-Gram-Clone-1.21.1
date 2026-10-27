package net.yizuz.playerogrammod.client.flight;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Shared flight pose math. The body's long axis follows the direction of travel
 * (including vertical movement), and the head counter-rotates so it looks that
 * way too.
 */
public final class RingFlightPose {
    public static final double MOVING_SPEED = 0.12D;
    private static final double FULL_BLEND_SPEED = 1.1D;

    public static boolean isMoving(AbstractClientPlayer player) {
        return speed(player) > MOVING_SPEED;
    }

    public static boolean isMovingBackward(AbstractClientPlayer player) {
        Vec3 movement = movement(player);
        double horizontalSq = movement.x * movement.x + movement.z * movement.z;
        if (horizontalSq <= MOVING_SPEED * MOVING_SPEED) {
            return false;
        }
        double yaw = Math.toRadians(player.getYRot());
        double dot = movement.x * -Math.sin(yaw) + movement.z * Math.cos(yaw);
        return dot < -0.15D * Math.sqrt(horizontalSq);
    }

    public static double speed(AbstractClientPlayer player) {
        return movement(player).length();
    }

    public static Vec3 movement(AbstractClientPlayer player) {
        Vec3 velocity = player.getDeltaMovement();
        double dx = player.getX() - player.xOld;
        double dy = player.getY() - player.yOld;
        double dz = player.getZ() - player.zOld;
        Vec3 tracked = new Vec3(dx, dy, dz);
        return velocity.lengthSqr() >= tracked.lengthSqr() ? velocity : tracked;
    }

    public static float moveBlend(AbstractClientPlayer player) {
        double speed = speed(player);
        if (speed <= MOVING_SPEED) {
            return 0.0F;
        }
        return Mth.clamp((float) ((speed - MOVING_SPEED) / (FULL_BLEND_SPEED - MOVING_SPEED)),
                0.0F, 1.0F);
    }

    public static float elevationDegrees(AbstractClientPlayer player) {
        Vec3 movement = movement(player);
        if (movement.lengthSqr() <= MOVING_SPEED * MOVING_SPEED) {
            return 0.0F;
        }
        double horizontal = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
        return (float) Math.toDegrees(Math.atan2(movement.y, horizontal));
    }

    public static float leanDegrees(AbstractClientPlayer player) {
        if (RingFlightClientState.isHovering(player.getUUID())) {
            return 0.0F;
        }
        float blend = moveBlend(player);
        if (blend <= 0.0F) {
            return 0.0F;
        }
        if (isMovingBackward(player)) {
            return -50.0F * blend;
        }
        return (90.0F - elevationDegrees(player)) * blend;
    }

    public static float headPitchRadians(AbstractClientPlayer player) {
        if (RingFlightClientState.isHovering(player.getUUID())) {
            return 0.0F;
        }
        float blend = moveBlend(player);
        if (blend <= 0.0F) {
            return 0.0F;
        }
        if (isMovingBackward(player)) {
            return (float) Math.toRadians(50.0D * blend);
        }
        return (float) Math.toRadians(-90.0D * blend);
    }

    private RingFlightPose() {
    }
}
