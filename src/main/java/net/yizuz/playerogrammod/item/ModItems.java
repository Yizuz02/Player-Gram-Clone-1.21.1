package net.yizuz.playerogrammod.item;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.yizuz.playerogrammod.PlayerOGramMod;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PlayerOGramMod.MOD_ID);

    public static final DeferredItem<Item> LANTERN_RING = ITEMS.register("lantern_ring",
            () -> new Item(new Item.Properties()));


    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}