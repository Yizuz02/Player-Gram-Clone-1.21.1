package net.yizuz.playerogrammod.client.flight;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.yizuz.playerogrammod.flight.RingFlightRules;
import net.yizuz.playerogrammod.network.RingFlightSetPayload;

/**
 * Handles the double-tap-jump toggle and the local safety checks that keep the
 * flight state honest when the ring is dropped, the player lands, or a vehicle
 * is entered.
 */
public final class RingFlightClientController {
    private static final long DOUBLE_TAP_WINDOW_MS = 320L;
    private static final int LANDING_GRACE_TICKS = 10;

    private static boolean lastJumpDown;
    private static long lastJumpTapMs;
    private static int activeTicks;
    private static boolean wasActive;

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            reset();
            return;
        }

        boolean active = RingFlightClientState.isActive(player.getUUID());
        if (active) {
            activeTicks++;
            if (!RingFlightRules.canFly(player)) {
                setFlight(player, false);
                active = false;
            } else if (player.onGround() && activeTicks > LANDING_GRACE_TICKS
                    && !minecraft.options.keyJump.isDown()) {
                setFlight(player, false);
                active = false;
            }
        } else {
            activeTicks = 0;
        }

        boolean jumpDown = minecraft.screen == null && minecraft.options.keyJump.isDown();
        if (jumpDown && !lastJumpDown) {
            long now = Util.getMillis();
            if (RingFlightRules.canFly(player) && now - lastJumpTapMs <= DOUBLE_TAP_WINDOW_MS) {
                setFlight(player, !RingFlightClientState.isActive(player.getUUID()));
                active = RingFlightClientState.isActive(player.getUUID());
                lastJumpTapMs = 0L;
            } else if (RingFlightRules.hasRing(player)) {
                lastJumpTapMs = now;
            } else {
                lastJumpTapMs = 0L;
            }
        }
        lastJumpDown = jumpDown;

        if (active && !wasActive) {
            RingFlightParticles.burst(player);
        }
        wasActive = active;
    }

    private static void setFlight(LocalPlayer player, boolean active) {
        if (active && !RingFlightRules.canFly(player)) {
            return;
        }
        RingFlightClientState.setActive(player.getUUID(), active);
        PacketDistributor.sendToServer(new RingFlightSetPayload(active));
    }

    public static void reset() {
        lastJumpDown = false;
        lastJumpTapMs = 0L;
        activeTicks = 0;
        wasActive = false;
    }

    private RingFlightClientController() {
    }
}
