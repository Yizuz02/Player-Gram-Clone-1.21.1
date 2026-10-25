package net.yizuz.playerogrammod.ring;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.yizuz.playerogrammod.item.ModItems;
import net.yizuz.playerogrammod.prompt.SystemPromptProvider;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class RingConfigs {
    public record Match(ItemStack stack, int slot, RingConfig config) {
    }

    public static RingConfig get(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return RingConfig.DEFAULT;
        }
        return stack.getOrDefault(ModDataComponents.RING_CONFIG.get(), RingConfig.DEFAULT);
    }

    public static void set(ItemStack stack, RingConfig config) {
        if (stack != null && !stack.isEmpty() && config != null) {
            stack.set(ModDataComponents.RING_CONFIG.get(), config);
        }
    }

    public static RingConfig ensureIdentity(ItemStack stack) {
        RingConfig config = get(stack);
        if (!config.hasRingId()) {
            config = config.withRingId(UUID.randomUUID());
            set(stack, config);
        }
        return config;
    }

    /**
     * Reassigns a copied ring's identity when the same ID is already present in an
     * earlier inventory slot. The earlier stack keeps the ID; the later copy becomes
     * an independent ring.
     */
    public static boolean ensureUnique(ServerPlayer player, ItemStack stack, int slot) {
        RingConfig config = get(stack);
        if (player == null || !config.hasRingId()) {
            return false;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (i == slot) {
                continue;
            }
            ItemStack other = inventory.getItem(i);
            if (!other.is(ModItems.LANTERN_RING.get())) {
                continue;
            }
            RingConfig otherConfig = get(other);
            if (config.ringId().equals(otherConfig.ringId())
                    && RingIdentityGuard.shouldReassign(slot, i)) {
                set(stack, config.withRingId(UUID.randomUUID()));
                return true;
            }
        }
        return false;
    }

    /**
     * Finds exactly one Lantern Ring in the player's inventory with this identity.
     * Multiple matches are treated as ambiguous and rejected.
     */
    @Nullable
    public static Match findUnique(Player player, UUID ringId) {
        if (player == null || ringId == null || RingConfig.UNASSIGNED.equals(ringId)) {
            return null;
        }
        Inventory inventory = player.getInventory();
        Match match = null;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(ModItems.LANTERN_RING.get())) {
                continue;
            }
            RingConfig config = get(stack);
            if (!ringId.equals(config.ringId())) {
                continue;
            }
            if (match != null) {
                return null;
            }
            match = new Match(stack, i, config);
        }
        return match;
    }

    public static boolean isRing(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ModItems.LANTERN_RING.get());
    }

    public static boolean hasIdentity(ItemStack stack) {
        return get(stack).hasRingId();
    }

    public static String effectivePrompt(RingConfig config) {
        if (config == null) {
            return SystemPromptProvider.instance().get();
        }
        return config.customPrompt()
                ? config.systemPrompt()
                : SystemPromptProvider.instance().get();
    }

    private RingConfigs() {
    }
}
