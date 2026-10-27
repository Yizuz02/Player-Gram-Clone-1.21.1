package net.yizuz.playerogrammod.client.flight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.yizuz.playerogrammod.sound.ModSounds;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Looping ring hum, one instance per actively flying player. It plays in both
 * hover and normal flight, follows the player, and stops with the flight state.
 */
public final class RingFlightLoopSound {
    private static final Map<UUID, FlightLoopSound> LOOPS = new HashMap<>();

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null) {
            clear();
            return;
        }
        Iterator<Map.Entry<UUID, FlightLoopSound>> iterator = LOOPS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, FlightLoopSound> entry = iterator.next();
            FlightLoopSound loop = entry.getValue();
            if (loop.isStopped() || !RingFlightClientState.isActive(entry.getKey())) {
                loop.stopNow();
                iterator.remove();
            }
        }
        for (AbstractClientPlayer player : minecraft.level.players()) {
            UUID playerId = player.getUUID();
            if (RingFlightClientState.isActive(playerId) && !LOOPS.containsKey(playerId)) {
                FlightLoopSound loop = new FlightLoopSound(player);
                LOOPS.put(playerId, loop);
                minecraft.getSoundManager().play(loop);
            }
        }
    }

    public static void clear() {
        for (FlightLoopSound loop : LOOPS.values()) {
            loop.stopNow();
        }
        LOOPS.clear();
    }

    private static final class FlightLoopSound extends AbstractTickableSoundInstance {
        private final AbstractClientPlayer player;

        private FlightLoopSound(AbstractClientPlayer player) {
            super(ModSounds.RING_HOVER.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.8F;
            this.pitch = 1.0F;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            updatePosition();
        }

        @Override
        public void tick() {
            if (player.isRemoved() || !RingFlightClientState.isActive(player.getUUID())) {
                stop();
                return;
            }
            updatePosition();
        }

        private void updatePosition() {
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
        }

        private void stopNow() {
            stop();
        }
    }

    private RingFlightLoopSound() {
    }
}
