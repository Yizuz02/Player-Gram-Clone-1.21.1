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
    public static final String RING_HOVER_ID = "ring_hover";
    public static final String RING_START_FLYING_ID = "ring_start_flying";
    public static final String RING_IMPULSE_FLYING_ID = "ring_impulse_flying";
    public static final String RING_BUTTON_PRESS_ID = "ring_button_press";
    public static final String RING_BUTTON_HOVER_ID = "ring_button_hover";
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, PlayerOGramMod.MOD_ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> RING_SOUND = SOUNDS.register(
            RING_SOUND_ID,
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(PlayerOGramMod.MOD_ID, RING_SOUND_ID)));
    public static final DeferredHolder<SoundEvent, SoundEvent> RING_HOVER = SOUNDS.register(
            RING_HOVER_ID,
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(PlayerOGramMod.MOD_ID, RING_HOVER_ID)));
    public static final DeferredHolder<SoundEvent, SoundEvent> RING_START_FLYING = SOUNDS.register(
            RING_START_FLYING_ID,
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(PlayerOGramMod.MOD_ID, RING_START_FLYING_ID)));
    public static final DeferredHolder<SoundEvent, SoundEvent> RING_IMPULSE_FLYING = SOUNDS.register(
            RING_IMPULSE_FLYING_ID,
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(PlayerOGramMod.MOD_ID, RING_IMPULSE_FLYING_ID)));
    public static final DeferredHolder<SoundEvent, SoundEvent> RING_BUTTON_PRESS = SOUNDS.register(
            RING_BUTTON_PRESS_ID,
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(PlayerOGramMod.MOD_ID, RING_BUTTON_PRESS_ID)));
    public static final DeferredHolder<SoundEvent, SoundEvent> RING_BUTTON_HOVER = SOUNDS.register(
            RING_BUTTON_HOVER_ID,
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(PlayerOGramMod.MOD_ID, RING_BUTTON_HOVER_ID)));

    public static void register(IEventBus eventBus) {
        SOUNDS.register(eventBus);
    }

    private ModSounds() {
    }
}
