package net.yizuz.playerogrammod.flight;

import net.minecraft.world.phys.Vec3;

/**
 * Pure vector helpers for the ring's superhero flight. Keeping the steering math
 * out of the client classes makes the control scheme testable.
 */
public final class FlightMath {
    public static Vec3 thrustDirection(Vec3 look, double forward, double strafe) {
        Vec3 normalizedLook = look.lengthSqr() > 1.0E-6 ? look.normalize() : Vec3.ZERO;
        Vec3 horizontalRight = new Vec3(-normalizedLook.z, 0.0, normalizedLook.x);
        if (horizontalRight.lengthSqr() > 1.0E-6) {
            horizontalRight = horizontalRight.normalize();
        } else {
            horizontalRight = Vec3.ZERO;
        }

        Vec3 forwardDirection;
        if (forward > 0.0) {
            forwardDirection = normalizedLook;
        } else if (forward < 0.0) {
            Vec3 backward = new Vec3(-normalizedLook.x, 0.0, -normalizedLook.z);
            forwardDirection = backward.lengthSqr() > 1.0E-6 ? backward.normalize() : Vec3.ZERO;
        } else {
            forwardDirection = Vec3.ZERO;
        }

        Vec3 direction = forwardDirection.scale(forward).add(horizontalRight.scale(strafe));
        return direction.lengthSqr() > 1.0E-6 ? direction.normalize() : Vec3.ZERO;
    }

    public static Vec3 clampSpeed(Vec3 velocity, double maxSpeed) {
        double length = velocity.length();
        if (length > maxSpeed && length > 1.0E-6) {
            return velocity.scale(maxSpeed / length);
        }
        return velocity;
    }

    private FlightMath() {
    }
}
