package net.yizuz.playerogrammod.network;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.yizuz.playerogrammod.flight.RingFlightManager;
import net.yizuz.playerogrammod.ring.RingValidation;

import java.util.concurrent.RejectedExecutionException;

public final class RingNetwork {
    public static void register(IEventBus eventBus) {
        eventBus.addListener(RingNetwork::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(RingConfigSavePayload.TYPE, RingConfigSavePayload.STREAM_CODEC,
                RingNetwork::handleSave);
        registrar.playToClient(RingConfigSaveResultPayload.TYPE, RingConfigSaveResultPayload.STREAM_CODEC,
                RingNetwork::handleSaveResult);
        registrar.playToServer(RingSkinRequestPayload.TYPE, RingSkinRequestPayload.STREAM_CODEC,
                RingNetwork::handleSkinRequest);
        registrar.playToClient(RingSkinResponsePayload.TYPE, RingSkinResponsePayload.STREAM_CODEC,
                RingNetwork::handleSkinResponse);
        registrar.playToServer(RingFlightSetPayload.TYPE, RingFlightSetPayload.STREAM_CODEC,
                RingNetwork::handleFlightSet);
        registrar.playToClient(RingFlightStatePayload.TYPE, RingFlightStatePayload.STREAM_CODEC,
                RingNetwork::handleFlightState);
        registrar.playToServer(RingFlightHoverSetPayload.TYPE, RingFlightHoverSetPayload.STREAM_CODEC,
                RingNetwork::handleFlightHoverSet);
        registrar.playToClient(RingFlightHoverStatePayload.TYPE, RingFlightHoverStatePayload.STREAM_CODEC,
                RingNetwork::handleFlightHoverState);
    }

    private static void handleFlightHoverSet(RingFlightHoverSetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                RingFlightManager.get().requestHover(player, payload.hover());
            }
        });
    }

    private static void handleFlightHoverState(RingFlightHoverStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> RingFlightClientBridge.onHoverState(payload));
    }

    private static void handleFlightSet(RingFlightSetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                RingFlightManager.get().request(player, payload.active());
            }
        });
    }

    private static void handleFlightState(RingFlightStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> RingFlightClientBridge.onState(payload));
    }

    private static void handleSave(RingConfigSavePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            RingConfigService.Result result = RingConfigService.save(player, payload);
            context.reply(new RingConfigSaveResultPayload(payload.ringId(), result.success(), result.message()));
        });
    }

    private static void handleSaveResult(RingConfigSaveResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> RingConfigClientBridge.onSaveResult(payload));
    }

    private static void handleSkinRequest(RingSkinRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            String username = payload.username() == null ? "" : payload.username().trim();
            if (RingValidation.validateSkinUsername(username) != null || username.isEmpty()) {
                replySkin(context, payload.requestId(), username, "", "");
                return;
            }
            MinecraftServer server = player.getServer();
            if (server == null || server.getProfileCache() == null) {
                replySkin(context, payload.requestId(), username, "", "");
                return;
            }
            try {
                server.getProfileCache().getAsync(username).whenComplete((optional, error) -> {
                    RingSkinResponsePayload response;
                    if (error != null || optional == null || optional.isEmpty()) {
                        response = new RingSkinResponsePayload(payload.requestId(), username, "", "");
                    } else {
                        GameProfile profile = optional.get();
                        Property textures = profile.getProperties().get("textures") == null
                                ? null
                                : profile.getProperties().get("textures").stream().findFirst().orElse(null);
                        response = new RingSkinResponsePayload(payload.requestId(), profile.getName(),
                                textures == null ? "" : textures.value(),
                                textures == null || textures.signature() == null ? "" : textures.signature());
                    }
                    try {
                        server.execute(() -> context.reply(response));
                    } catch (RejectedExecutionException e) {
                        // The server is stopping; the screen will time out or close.
                    }
                });
            } catch (RuntimeException e) {
                replySkin(context, payload.requestId(), username, "", "");
            }
        });
    }

    private static void replySkin(IPayloadContext context, java.util.UUID requestId,
                                  String username, String texture, String signature) {
        RingSkinResponsePayload response = new RingSkinResponsePayload(
                requestId, username == null ? "" : username,
                texture == null ? "" : texture, signature == null ? "" : signature);
        try {
            context.reply(response);
        } catch (RuntimeException e) {
            PacketDistributor.sendToPlayer((ServerPlayer) context.player(), response);
        }
    }

    private static void handleSkinResponse(RingSkinResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> RingConfigClientBridge.onSkinResponse(payload));
    }

    private RingNetwork() {
    }
}
