package net.yizuz.playerogrammod.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.yizuz.playerogrammod.PlayerOGramMod;
import net.yizuz.playerogrammod.item.LanternRingItem;
import net.yizuz.playerogrammod.ring.RingConfig;
import net.yizuz.playerogrammod.ring.RingConfigs;

import java.util.UUID;

@EventBusSubscriber(modid = PlayerOGramMod.MOD_ID, value = Dist.CLIENT)
public final class RingConfigClientEvents {
    private static final RingHoldTracker HOLD = new RingHoldTracker();

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (!(minecraft.screen instanceof AbstractContainerScreen<?> screen) || player == null) {
            HOLD.cancel();
            return;
        }

        Slot slot = screen.getSlotUnderMouse();
        ItemStack stack = slot != null && slot.hasItem() ? slot.getItem() : ItemStack.EMPTY;
        UUID ringId = null;
        RingConfig config = null;
        if (slot != null && slot.container == player.getInventory()
                && stack.getItem() instanceof LanternRingItem) {
            config = RingConfigs.get(stack);
            if (config.hasRingId()) {
                ringId = config.ringId();
            }
        }

        if (HOLD.update(ringId, Screen.hasShiftDown(), Util.getMillis())) {
            HOLD.opened();
            minecraft.setScreen(new RingConfigScreen(screen, config, player.getGameProfile().getName()));
        }
    }

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>) || !HOLD.isTracking()) {
            return;
        }
        float progress = HOLD.progress(Util.getMillis());
        if (progress <= 0.0f) {
            return;
        }
        GuiGraphics guiGraphics = event.getGuiGraphics();
        int x = event.getMouseX() + 10;
        int y = event.getMouseY() - 12;
        int filled = Math.max(0, Math.min(50, (int) (progress * 50.0f)));
        guiGraphics.fill(x - 2, y - 2, x + 52, y + 8, 0xCC000000);
        guiGraphics.fill(x, y, x + 50, y + 6, 0x663CFF88);
        guiGraphics.fill(x, y, x + filled, y + 6, 0xFF3CFF88);
        guiGraphics.drawString(Minecraft.getInstance().font,
                Component.translatable("screen.playerogrammod.ring_config.hold"),
                x, y + 10, 0xFFFFFFFF, true);
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?>) {
            HOLD.click(Screen.hasShiftDown());
        }
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?>) {
            HOLD.cancel();
        }
    }

    private RingConfigClientEvents() {
    }
}
