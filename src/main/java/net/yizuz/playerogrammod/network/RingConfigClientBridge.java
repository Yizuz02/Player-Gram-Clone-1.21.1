package net.yizuz.playerogrammod.network;

import java.util.function.Consumer;

/**
 * Dist-safe handoff for clientbound ring payloads. The common network class only
 * touches this bridge; the client screen installs its handlers here.
 */
public final class RingConfigClientBridge {
    private static volatile Consumer<RingConfigSaveResultPayload> saveHandler;
    private static volatile Consumer<RingSkinResponsePayload> skinHandler;

    public static void setHandlers(Consumer<RingConfigSaveResultPayload> save,
                                   Consumer<RingSkinResponsePayload> skin) {
        saveHandler = save;
        skinHandler = skin;
    }

    public static void clearHandlers() {
        saveHandler = null;
        skinHandler = null;
    }

    public static void onSaveResult(RingConfigSaveResultPayload payload) {
        Consumer<RingConfigSaveResultPayload> handler = saveHandler;
        if (handler != null) {
            handler.accept(payload);
        }
    }

    public static void onSkinResponse(RingSkinResponsePayload payload) {
        Consumer<RingSkinResponsePayload> handler = skinHandler;
        if (handler != null) {
            handler.accept(payload);
        }
    }

    private RingConfigClientBridge() {
    }
}
