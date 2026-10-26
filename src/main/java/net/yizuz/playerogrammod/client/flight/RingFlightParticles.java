package net.yizuz.playerogrammod.client.flight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Green energy trail. Particles are client-side, driven by the synced flight
 * state, so every viewer sees the trail without extra server traffic.
 */
public final class RingFlightParticles {
    private static final Vector3f GREEN = new Vector3f(0.05F, 0.90F, 0.22F);

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        for (AbstractClientPlayer player : level.players()) {
            if (!RingFlightClientState.isActive(player.getUUID())) {
                continue;
            }
            if (minecraft.player != null && player.distanceToSqr(minecraft.player) > 64.0 * 64.0) {
                continue;
            }

            Vec3 movement = player.getDeltaMovement();
            double speed = movement.length();
            if (speed < 0.12 && player.tickCount % 4 != 0) {
                continue;
            }

            int count = speed < 0.12 ? 1 : Mth.clamp((int) (speed * 2.0) + 1, 1, 6);
            for (int i = 0; i < count; i++) {
                double x = player.getX() + (level.random.nextDouble() - 0.5) * 0.9;
                double y = player.getY() + 0.2 + level.random.nextDouble() * 1.6;
                double z = player.getZ() + (level.random.nextDouble() - 0.5) * 0.9;
                if (speed > 0.25) {
                    x -= movement.x * 0.55;
                    y -= movement.y * 0.55;
                    z -= movement.z * 0.55;
                }

                double vx = -movement.x * 0.08 + (level.random.nextDouble() - 0.5) * 0.03;
                double vy = -movement.y * 0.08 + (level.random.nextDouble() - 0.5) * 0.03;
                double vz = -movement.z * 0.08 + (level.random.nextDouble() - 0.5) * 0.03;
                level.addParticle(new DustParticleOptions(GREEN, 0.9F + level.random.nextFloat() * 0.5F),
                        x, y, z, vx, vy, vz);

                if (level.random.nextFloat() < 0.14F) {
                    level.addParticle(ParticleTypes.END_ROD, x, y, z, vx * 0.4, vy * 0.4, vz * 0.4);
                }
            }
        }
    }

    public static void burst(AbstractClientPlayer player) {
        if (!(player.level() instanceof ClientLevel level)) {
            return;
        }
        for (int i = 0; i < 36; i++) {
            double dx = (level.random.nextDouble() - 0.5) * 1.6;
            double dy = (level.random.nextDouble() - 0.5) * 1.8;
            double dz = (level.random.nextDouble() - 0.5) * 1.6;
            level.addParticle(new DustParticleOptions(GREEN, 1.1F + level.random.nextFloat() * 0.7F),
                    player.getX() + dx, player.getY() + 1.0 + dy, player.getZ() + dz,
                    dx * 0.12, dy * 0.12, dz * 0.12);
        }
    }

    private RingFlightParticles() {
    }
}
