package net.yizuz.playerogrammod.item;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.yizuz.playerogrammod.PlayerOGramMod;

public final class ModItems {
    public static final String LANTERN_RING_ID = "lantern_ring";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PlayerOGramMod.MOD_ID);

    public static final DeferredItem<LanternRingItem> LANTERN_RING = ITEMS.register(LANTERN_RING_ID,
            () -> new LanternRingItem(new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    private ModItems() {
    }
}
