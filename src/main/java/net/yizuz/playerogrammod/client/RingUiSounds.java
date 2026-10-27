package net.yizuz.playerogrammod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.yizuz.playerogrammod.sound.ModSounds;

final class RingUiSounds {
    static void playHover() {
        play(ModSounds.RING_BUTTON_HOVER.get(), 1.12F, 0.22F);
    }

    static void playPress() {
        play(ModSounds.RING_BUTTON_PRESS.get(), 1.0F, 0.55F);
    }

    private static void play(SoundEvent event, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(event, pitch, volume));
    }

    private RingUiSounds() {
    }
}
