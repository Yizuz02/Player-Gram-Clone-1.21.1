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
        float blend = moveBlend(player);
        if (blend <= 0.0F) {
            return 0.0F;
        }
        return (90.0F - elevationDegrees(player)) * blend;
    }

    public static float headPitchRadians(AbstractClientPlayer player) {
        return (float) Math.toRadians(-90.0D * moveBlend(player));
    }

    private RingFlightPose() {
    }
}
