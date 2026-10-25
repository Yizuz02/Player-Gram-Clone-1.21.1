package net.yizuz.playerogrammod.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.yizuz.playerogrammod.PlayerOGramMod;

public final class ModEntities {
    public static final String HOLOGRAM_ID = "hologram";
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, PlayerOGramMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<HologramEntity>> HOLOGRAM =
            ENTITY_TYPES.register(HOLOGRAM_ID, () -> EntityType.Builder
                    .of(HologramEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .noSave()
                    .build(HOLOGRAM_ID));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
        eventBus.addListener(ModEntities::registerAttributes);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(HOLOGRAM.get(), attributes().build());
    }

    public static AttributeSupplier.Builder attributes() {
        return HologramEntity.createAttributes();
    }

    private ModEntities() {
    }
}
