package net.yizuz.playerogrammod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.yizuz.playerogrammod.chat.ChatConversationService;
import net.yizuz.playerogrammod.ring.RingConfig;
import net.yizuz.playerogrammod.ring.RingConfigs;
import net.yizuz.playerogrammod.ring.RingHoldState;
import net.yizuz.playerogrammod.ring.RingTooltip;

import java.util.List;
import java.util.UUID;

public final class LanternRingItem extends Item {
    public LanternRingItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(ChatFormatting.GREEN);
    }

    @Override
    public void verifyComponentsAfterLoad(ItemStack stack) {
        RingConfig config = RingConfigs.get(stack);
        if (!config.hasRingId()) {
            RingConfigs.set(stack, config.withRingId(UUID.randomUUID()));
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player)) {
            return;
        }
        RingConfigs.ensureIdentity(stack);
        if (player.tickCount % 20 == 0) {
            RingConfigs.ensureUnique(player, stack, slotId);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        RingConfig config = RingConfigs.get(stack);
        tooltip.add(Component.translatable("tooltip.playerogrammod.lantern_ring.hold_shift")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.playerogrammod.lantern_ring.flight")
                .withStyle(ChatFormatting.DARK_AQUA));
        if (RingHoldState.isActiveFor(config.ringId())) {
            int length = 20;
            int filled = Math.min(length,
                    (int) Math.ceil(RingHoldState.progressFor(config.ringId()) * length));
            tooltip.add(Component.literal(RingTooltip.progressBar(length, filled))
                    .withStyle(ChatFormatting.GREEN));
        }

        if (config.displayName().isBlank()) {
            tooltip.add(Component.translatable("tooltip.playerogrammod.lantern_ring.hologram_owner")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.playerogrammod.lantern_ring.hologram",
                    config.displayName()).withStyle(ChatFormatting.DARK_GRAY));
        }
        
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            ChatConversationService.get().onRingUse(serverPlayer, hand);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        return use(context.getLevel(), player, context.getHand()).getResult();
    }
}
