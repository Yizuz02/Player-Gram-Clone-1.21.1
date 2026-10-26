package net.yizuz.playerogrammod.flight;

import net.minecraft.world.entity.player.Player;
import net.yizuz.playerogrammod.ring.RingConfigs;

public final class RingFlightRules {
    public static boolean hasRing(Player player) {
        return player != null
                && (RingConfigs.isRing(player.getMainHandItem())
                || RingConfigs.isRing(player.getOffhandItem()));
    }

    public static boolean canFly(Player player) {
        return hasRing(player)
                && player.isAlive()
                && !player.isSpectator()
                && !player.isPassenger();
    }

    private RingFlightRules() {
    }
}
