package net.yizuz.playerogrammod.client.flight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.yizuz.playerogrammod.PlayerOGramMod;
import net.yizuz.playerogrammod.flight.RingFlightRules;
import net.yizuz.playerogrammod.network.RingFlightClientBridge;
import net.yizuz.playerogrammod.network.RingFlightHoverSetPayload;

@EventBusSubscriber(modid = PlayerOGramMod.MOD_ID, value = Dist.CLIENT)
public final class RingFlightClientEvents {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        RingFlightClientBridge.setHandler(payload ->
                RingFlightClientState.setActive(payload.playerId(), payload.active()));
        RingFlightClientBridge.setHoverHandler(payload ->
                RingFlightClientState.setHovering(payload.playerId(), payload.hover()));
        RingFlightAnimations.register();
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(RingFlightKeybinds.HOVER);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        handleHoverToggle(minecraft);
        RingFlightClientController.tick(minecraft);
        RingFlightAnimations.tick(minecraft);
        RingFlightLoopSound.tick(minecraft);
    }

    private static void handleHoverToggle(Minecraft minecraft) {
        while (RingFlightKeybinds.HOVER.consumeClick()) {
            if (minecraft.screen != null) {
                continue;
            }
            LocalPlayer player = minecraft.player;
            if (player == null || !RingFlightRules.hasRing(player)) {
                continue;
            }
            boolean hover = !RingFlightClientState.isHovering(player.getUUID());
            RingFlightClientState.setHovering(player.getUUID(), hover);
            PacketDistributor.sendToServer(new RingFlightHoverSetPayload(hover));
            player.displayClientMessage(Component.translatable(hover
                    ? "playerogrammod.flight.hover_on"
                    : "playerogrammod.flight.hover_off"), true);
        }
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
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        RingFlightPostOutline.onRenderStage(event);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui
                || !RingFlightRules.hasRing(player)) {
            return;
        }
        boolean hover = RingFlightClientState.isHovering(player.getUUID());
        Component label = Component.translatable(hover
                ? "playerogrammod.hud.hover"
                : "playerogrammod.hud.flying");
        GuiGraphics graphics = event.getGuiGraphics();
        int width = minecraft.font.width(label) + 10;
        graphics.fill(4, 4, 4 + width, 17, 0x90000000);
        graphics.drawString(minecraft.font, label, 9, 7,
                hover ? 0xFF6CFF9A : 0xFF9FDFFF, true);
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        if (event.getPlayer() instanceof LocalPlayer player
                && RingFlightClientState.isActive(player.getUUID())
                && !RingFlightClientState.isHovering(player.getUUID())) {
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
        RingFlightLoopSound.clear();
    }

    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        RingFlightClientState.setActive(event.getOldPlayer().getUUID(), false);
        RingFlightPhysics.reset();
        RingFlightClientController.reset();
        RingFlightOutline.reset();
        RingFlightLoopSound.clear();
    }

    private RingFlightClientEvents() {
    }
}
