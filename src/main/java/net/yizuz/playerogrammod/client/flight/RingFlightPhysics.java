package net.yizuz.playerogrammod.client.flight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.yizuz.playerogrammod.flight.FlightMath;

/**
 * Client-owned movement for the ring flight. The server keeps the player
 * gravity-free; this class turns WASD/look into momentum-based superhero motion
 * with a hover state when no direction is held.
 */
public final class RingFlightPhysics {
    private static final double CRUISE_SPEED = 1.45D;
    private static final double BOOST_SPEED = 2.75D;
    private static final double CRUISE_ACCEL = 0.14D;
    private static final double BOOST_ACCEL = 0.24D;
    private static final double VERTICAL_ACCEL = 0.10D;
    private static final double HOVER_DRAG = 0.82D;
    private static final double VERTICAL_DRAG = 0.90D;
    private static final double HOVER_SPEED = 0.62D;
    private static final double HOVER_SPRINT_SPEED = 1.15D;
    private static final double HOVER_VERTICAL_SPEED = 0.22D;

    private static boolean noGravityApplied;
    private static LocalPlayer lastPlayer;

    public static void preTick(LocalPlayer player) {
        boolean active = RingFlightClientState.isActive(player.getUUID());
        if (!active) {
            restoreGravity(player);
            return;
        }
        if (player != lastPlayer) {
            noGravityApplied = false;
            lastPlayer = player;
        }
        if (!noGravityApplied) {
            player.setNoGravity(true);
            noGravityApplied = true;
        }
        player.resetFallDistance();
        if (player.isFallFlying()) {
            player.stopFallFlying();
        }
    }

    public static void postTick(LocalPlayer player) {
        if (!RingFlightClientState.isActive(player.getUUID())) {
            return;
        }
        if (RingFlightClientState.isHovering(player.getUUID())) {
            applyHoverMovement(player);
            return;
        }
        player.resetFallDistance();
        if (player.isFallFlying()) {
            player.stopFallFlying();
        }

        Minecraft minecraft = Minecraft.getInstance();
        boolean inputAllowed = minecraft.screen == null;
        boolean forward = inputAllowed && minecraft.options.keyUp.isDown();
        boolean backward = inputAllowed && minecraft.options.keyDown.isDown();
        boolean left = inputAllowed && minecraft.options.keyLeft.isDown();
        boolean right = inputAllowed && minecraft.options.keyRight.isDown();
        boolean ascend = inputAllowed && minecraft.options.keyJump.isDown();
        boolean descend = inputAllowed && minecraft.options.keyShift.isDown();
        boolean boost = player.isSprinting() || (inputAllowed && minecraft.options.keySprint.isDown());

        double forwardInput = (forward ? 1.0 : 0.0) - (backward ? 1.0 : 0.0);
        double strafeInput = (right ? 1.0 : 0.0) - (left ? 1.0 : 0.0);
        Vec3 thrust = FlightMath.thrustDirection(player.getLookAngle(), forwardInput, strafeInput);

        Vec3 velocity = player.getDeltaMovement();
        if (thrust.lengthSqr() > 1.0E-6) {
            velocity = velocity.add(thrust.scale(boost ? BOOST_ACCEL : CRUISE_ACCEL));
        } else {
            velocity = velocity.scale(HOVER_DRAG);
        }

        if (ascend) {
            velocity = velocity.add(0.0, VERTICAL_ACCEL, 0.0);
        }
        if (descend) {
            velocity = velocity.subtract(0.0, VERTICAL_ACCEL, 0.0);
        }
        if (!ascend && !descend && forwardInput == 0.0) {
            velocity = new Vec3(velocity.x, velocity.y * VERTICAL_DRAG, velocity.z);
        }
        if (forwardInput == 0.0 && strafeInput == 0.0) {
            velocity = new Vec3(velocity.x * HOVER_DRAG, velocity.y, velocity.z * HOVER_DRAG);
        }

        velocity = FlightMath.clampSpeed(velocity, boost ? BOOST_SPEED : CRUISE_SPEED);
        if (velocity.lengthSqr() < 4.0E-4 && forwardInput == 0.0 && strafeInput == 0.0
                && !ascend && !descend) {
            velocity = Vec3.ZERO;
        }
        player.setDeltaMovement(velocity);
    }

    public static void reset() {
        if (lastPlayer != null && noGravityApplied) {
            lastPlayer.setNoGravity(false);
        }
        noGravityApplied = false;
        lastPlayer = null;
    }

    private static void applyHoverMovement(LocalPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean inputAllowed = minecraft.screen == null;
        boolean forward = inputAllowed && minecraft.options.keyUp.isDown();
        boolean backward = inputAllowed && minecraft.options.keyDown.isDown();
        boolean left = inputAllowed && minecraft.options.keyLeft.isDown();
        boolean right = inputAllowed && minecraft.options.keyRight.isDown();
        boolean ascend = inputAllowed && minecraft.options.keyJump.isDown();
        boolean descend = inputAllowed && minecraft.options.keyShift.isDown();
        boolean sprint = player.isSprinting() || (inputAllowed && minecraft.options.keySprint.isDown());

        double forwardInput = (forward ? 1.0 : 0.0) - (backward ? 1.0 : 0.0);
        double strafeInput = (right ? 1.0 : 0.0) - (left ? 1.0 : 0.0);
        double yaw = Math.toRadians(player.getYRot());
        Vec3 forwardDirection = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 rightDirection = new Vec3(-forwardDirection.z, 0.0, forwardDirection.x);
        Vec3 horizontal = forwardDirection.scale(forwardInput).add(rightDirection.scale(strafeInput));
        if (horizontal.lengthSqr() > 1.0) {
            horizontal = horizontal.normalize();
        }

        double horizontalSpeed = sprint ? HOVER_SPRINT_SPEED : HOVER_SPEED;
        double verticalSpeed = ascend ? HOVER_VERTICAL_SPEED : descend ? -HOVER_VERTICAL_SPEED : 0.0;
        player.setDeltaMovement(horizontal.x * horizontalSpeed, verticalSpeed,
                horizontal.z * horizontalSpeed);
        player.resetFallDistance();
        if (player.isFallFlying()) {
            player.stopFallFlying();
        }
    }

    private static void restoreGravity(LocalPlayer player) {
        if (noGravityApplied && player == lastPlayer) {
            player.setNoGravity(false);
        }
        noGravityApplied = false;
    }

    private RingFlightPhysics() {
    }
}
