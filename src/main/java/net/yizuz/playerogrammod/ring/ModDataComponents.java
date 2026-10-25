package net.yizuz.playerogrammod.ring;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.yizuz.playerogrammod.PlayerOGramMod;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PlayerOGramMod.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RingConfig>> RING_CONFIG =
            DATA_COMPONENTS.registerComponentType("ring_config", builder -> builder
                    .persistent(RingConfigCodecs.CODEC)
                    .networkSynchronized(RingConfigCodecs.STREAM_CODEC)
                    .cacheEncoding());

    public static void register(IEventBus eventBus) {
        DATA_COMPONENTS.register(eventBus);
    }

    private ModDataComponents() {
    }
}
