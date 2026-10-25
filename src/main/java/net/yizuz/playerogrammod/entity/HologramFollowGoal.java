package net.yizuz.playerogrammod.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.yizuz.playerogrammod.hologram.HologramSettings;

import java.util.EnumSet;

/**
 * Re-paths toward the owner when they move out of range, and stops once close.
 */
public final class HologramFollowGoal extends Goal {
    private static final int REPATH_TICKS = 20;

    private final HologramEntity hologram;
    private int repathCooldown;

    public HologramFollowGoal(HologramEntity hologram) {
        this.hologram = hologram;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return canFollow();
    }

    @Override
    public boolean canContinueToUse() {
        return canContinue();
    }

    private boolean canFollow() {
        ServerPlayer owner = validOwner();
        if (owner == null) {
            return false;
        }
        HologramSettings settings = HologramSettings.current();
        return HologramSettings.shouldMove(this.hologram.distanceToSqr(owner), settings.followStartDistance());
    }

    private boolean canContinue() {
        ServerPlayer owner = validOwner();
        if (owner == null) {
            return false;
        }
        HologramSettings settings = HologramSettings.current();
        return !HologramSettings.shouldStop(this.hologram.distanceToSqr(owner), settings.followStopDistance());
    }

    private ServerPlayer validOwner() {
        if (this.hologram.isDematerializing() || this.hologram.level().isClientSide()) {
            return null;
        }
        ServerPlayer owner = this.hologram.getOwner();
        if (owner == null || owner.level() != this.hologram.level()) {
            return null;
        }
        return owner;
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
    }

    @Override
    public void tick() {
        ServerPlayer owner = this.hologram.getOwner();
        if (owner == null) {
            return;
        }
        HologramSettings settings = HologramSettings.current();
        if (HologramSettings.shouldStop(this.hologram.distanceToSqr(owner), settings.followStopDistance())) {
            this.hologram.getNavigation().stop();
            return;
        }
        if (--this.repathCooldown <= 0) {
            this.hologram.getNavigation().moveTo(owner, settings.followSpeed());
            this.repathCooldown = REPATH_TICKS;
        }
    }

    @Override
    public void stop() {
        this.hologram.getNavigation().stop();
    }
}
