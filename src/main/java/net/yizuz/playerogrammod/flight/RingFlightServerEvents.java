package net.yizuz.playerogrammod.flight;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.yizuz.playerogrammod.PlayerOGramMod;
import net.yizuz.playerogrammod.network.RingFlightHoverStatePayload;
import net.yizuz.playerogrammod.network.RingFlightStatePayload;

import java.util.UUID;

@EventBusSubscriber(modid = PlayerOGramMod.MOD_ID)
public final class RingFlightServerEvents {
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        RingFlightManager.get().tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        RingFlightManager manager = RingFlightManager.get();
        manager.resetOnLogin(player);
        for (UUID active : manager.activePlayers()) {
            PacketDistributor.sendToPlayer(player, new RingFlightStatePayload(active, true));
            PacketDistributor.sendToPlayer(player,
                    new RingFlightHoverStatePayload(active, manager.isHovering(active)));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RingFlightManager.get().deactivate(player);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RingFlightManager.get().deactivate(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        RingFlightManager.get().deactivateAll(event.getServer());
    }

    private RingFlightServerEvents() {
    }
}
