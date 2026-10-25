package net.yizuz.playerogrammod.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.yizuz.playerogrammod.PlayerOGramMod;
import net.yizuz.playerogrammod.entity.ModEntities;

@EventBusSubscriber(modid = PlayerOGramMod.MOD_ID, value = Dist.CLIENT)
public final class HologramClientEvents {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.HOLOGRAM.get(), HologramRenderer::new);
    }

    private HologramClientEvents() {
    }
}
