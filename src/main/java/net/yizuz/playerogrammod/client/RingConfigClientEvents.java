package net.yizuz.playerogrammod.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
import net.yizuz.playerogrammod.ring.RingHoldState;

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
            RingHoldState.clear();
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

        long now = Util.getMillis();
        boolean open = HOLD.update(ringId, Screen.hasShiftDown(), now);
        if (HOLD.isTracking()) {
            RingHoldState.update(ringId, HOLD.progress(now), true);
        } else {
            RingHoldState.clear();
        }
        if (open) {
            HOLD.opened();
            RingHoldState.clear();
            minecraft.setScreen(new RingConfigScreen(screen, config, player.getGameProfile().getName()));
        }
    }

    /**
     * Runs before the screen renders its tooltip, keeping the lore progress bar
     * smooth without a separate inventory overlay.
     */
    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        if (HOLD.isTracking()) {
            RingHoldState.update(HOLD.ringId(), HOLD.progress(Util.getMillis()), true);
        } else {
            RingHoldState.clear();
        }
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?>) {
            HOLD.click(Screen.hasShiftDown());
            RingHoldState.clear();
        }
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?>) {
            HOLD.cancel();
            RingHoldState.clear();
        }
    }

    private RingConfigClientEvents() {
    }
}
