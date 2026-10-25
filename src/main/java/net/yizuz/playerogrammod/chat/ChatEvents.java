package net.yizuz.playerogrammod.chat;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.yizuz.playerogrammod.PlayerOGramMod;
import net.yizuz.playerogrammod.hologram.HologramService;

@EventBusSubscriber(modid = PlayerOGramMod.MOD_ID)
public final class ChatEvents {
    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        ChatConversationService.get().onChat(event.getPlayer(), event);
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        ChatConversationService.get().onServerStarting(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ChatConversationService.get().onServerStopping();
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ChatConversationService.get().onEquipmentChanged(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ChatConversationService.get().onPlayerLogout(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ChatConversationService.get().onPlayerChangedDimension(player);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ChatConversationService.get().onPlayerDeath(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        HologramService.get().onServerTick(event.getServer());
    }

    private ChatEvents() {
    }
}
