package net.yizuz.playerogrammod.client.flight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.yizuz.playerogrammod.PlayerOGramMod;
import net.yizuz.playerogrammod.network.RingFlightClientBridge;

@EventBusSubscriber(modid = PlayerOGramMod.MOD_ID, value = Dist.CLIENT)
public final class RingFlightClientEvents {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        RingFlightClientBridge.setHandler(payload ->
                RingFlightClientState.setActive(payload.playerId(), payload.active()));
        RingFlightAnimations.register();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        RingFlightClientController.tick(minecraft);
        RingFlightOutline.tick(minecraft);
        RingFlightParticles.tick(minecraft);
        RingFlightAnimations.tick(minecraft);
    }

    @SubscribeEvent
    public static void onPlayerTickPre(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof LocalPlayer player) {
            RingFlightPhysics.preTick(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof LocalPlayer player) {
            RingFlightPhysics.postTick(player);
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        RingFlightOutline.onRenderPre(event);
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        RingFlightOutline.onRenderPost(event);
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        if (event.getPlayer() instanceof LocalPlayer player
                && RingFlightClientState.isActive(player.getUUID())) {
            double speed = player.getDeltaMovement().length();
            float boost = Mth.clamp((float) (speed / 3.0), 0.0F, 1.0F);
            event.setNewFovModifier(event.getNewFovModifier() * (1.0F + boost * 0.16F));
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        RingFlightClientState.clear();
        RingFlightPhysics.reset();
        RingFlightClientController.reset();
        RingFlightOutline.reset();
    }

    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        RingFlightClientState.setActive(event.getOldPlayer().getUUID(), false);
        RingFlightPhysics.reset();
        RingFlightClientController.reset();
        RingFlightOutline.reset();
    }

    private RingFlightClientEvents() {
    }
}
