package net.yizuz.playerogrammod.sound;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.yizuz.playerogrammod.PlayerOGramMod;

public final class ModSounds {
    public static final String RING_SOUND_ID = "ring_sound";
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, PlayerOGramMod.MOD_ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> RING_SOUND = SOUNDS.register(
            RING_SOUND_ID,
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(PlayerOGramMod.MOD_ID, RING_SOUND_ID)));

    public static void register(IEventBus eventBus) {
        SOUNDS.register(eventBus);
    }

    private ModSounds() {
    }
}
