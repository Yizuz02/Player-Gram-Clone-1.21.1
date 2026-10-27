package net.yizuz.playerogrammod.client.flight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.yizuz.playerogrammod.sound.ModSounds;

/**
 * One-shot surge for normal flight acceleration bursts. Fires when horizontal
 * thrust starts, or when boost is engaged while already thrusting.
 */
public final class RingFlightAccelerationSound {
    private static boolean wasAccelerating;
    private static boolean wasBoost;

    public static void tick(LocalPlayer player, boolean accelerating, boolean boost) {
        if (accelerating && (!wasAccelerating || (boost && !wasBoost))) {
            Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(
                    ModSounds.RING_IMPULSE_FLYING.get(),
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F,
                    SoundInstance.createUnseededRandom(),
                    player.getX(),
                    player.getY(),
                    player.getZ()));
        }
        wasAccelerating = accelerating;
        wasBoost = boost;
    }

    public static void reset() {
        wasAccelerating = false;
        wasBoost = false;
    }

    private RingFlightAccelerationSound() {
    }
}
