package net.yizuz.playerogrammod.network;

import java.util.function.Consumer;

/**
 * Dist-safe handoff for the clientbound flight state. The common network class
 * never touches client-only state directly.
 */
public final class RingFlightClientBridge {
    private static volatile Consumer<RingFlightStatePayload> stateHandler;

    public static void setHandler(Consumer<RingFlightStatePayload> handler) {
        stateHandler = handler;
    }

    public static void onState(RingFlightStatePayload payload) {
        Consumer<RingFlightStatePayload> handler = stateHandler;
        if (handler != null) {
            handler.accept(payload);
        }
    }

    private RingFlightClientBridge() {
    }
}
