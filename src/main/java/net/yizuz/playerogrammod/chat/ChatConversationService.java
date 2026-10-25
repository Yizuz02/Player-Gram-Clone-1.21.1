package net.yizuz.playerogrammod.chat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.yizuz.playerogrammod.Config;
import net.yizuz.playerogrammod.ai.AiService;
import net.yizuz.playerogrammod.ai.AiState;
import net.yizuz.playerogrammod.ai.AiStatus;
import net.yizuz.playerogrammod.hologram.HologramService;
import net.yizuz.playerogrammod.item.LanternRingItem;
import net.yizuz.playerogrammod.item.ModItems;
import net.yizuz.playerogrammod.prompt.SystemPromptProvider;
import net.yizuz.playerogrammod.ring.RingConfig;
import net.yizuz.playerogrammod.ring.RingConfigs;
import net.yizuz.playerogrammod.ring.RingSessionManager;
import net.yizuz.playerogrammod.ring.RingSessionRules;
import net.yizuz.playerogrammod.ring.RingValidation;
import net.yizuz.playerogrammod.sound.ModSounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

public final class ChatConversationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatConversationService.class);
    private static final ChatConversationService INSTANCE = new ChatConversationService();

    private static final String ACTIVATED_MESSAGE = "The connection is established.";
    private static final String DEACTIVATED_MESSAGE = "The connection is closed.";
    private static final String NOT_READY_MESSAGE =
            "The connection isn't ready yet. Try again once the AI has finished preparing.";
    private static final String AI_DISABLED_MESSAGE = "The AI is currently disabled on this server.";
    private static final String RING_REQUIRED_MESSAGE =
            "The Lantern Ring must be held to keep the connection open.";

    private volatile MinecraftServer server;
    private volatile ConversationPipeline pipeline;

    private ChatConversationService() {
    }

    public static ChatConversationService get() {
        return INSTANCE;
    }

    public void onServerStarting(MinecraftServer server) {
        this.server = server;
        SystemPromptProvider.instance().reload();
        RingSessionManager.get().clear();

        int maxMessages = Config.HISTORY_MESSAGES.get();
        int maxChars = Config.HISTORY_MAX_CHARS.get();
        RingSessionManager sessions = RingSessionManager.get();
        this.pipeline = new ConversationPipeline(
                new ConversationManager(maxMessages, maxChars),
                SystemPromptProvider.instance(),
                AiService.get()::complete,
                new Sink(),
                server::execute,
                sessions::isCurrent,
                playerId -> {
                    String prompt = sessions.prompt(playerId);
                    return prompt == null ? SystemPromptProvider.instance().get() : prompt;
                });
        HologramService.get().onServerStarting(server);
        LOGGER.info("Ring chat pipeline ready (history {} messages / {} chars).", maxMessages, maxChars);
    }

    public void onServerStopping() {
        HologramService.get().onServerStopping();
        ConversationPipeline current = pipeline;
        if (current != null) {
            current.clearConversations();
        }
        RingSessionManager.get().clear();
        pipeline = null;
        server = null;
    }

    public void onRingUse(ServerPlayer player, InteractionHand hand) {
        MinecraftServer currentServer = server;
        if (player == null || currentServer == null || pipeline == null) {
            return;
        }

        UUID playerId = player.getUUID();
        RingSessionManager sessions = RingSessionManager.get();

        if (sessions.isActive(playerId)) {
            deactivate(player, DEACTIVATED_MESSAGE, 0.8f);
            return;
        }

        AiStatus status = AiService.get().status();
        boolean enabled = aiEnabled();
        boolean ready = status.state() == AiState.READY;
        if (!RingSessionRules.canActivate(enabled, ready)) {
            ChatPresenter.sendStatus(currentServer, playerId,
                    enabled ? NOT_READY_MESSAGE : AI_DISABLED_MESSAGE);
            LOGGER.info("Ring activation refused for {}: aiEnabled={}, state={}, lastError={}",
                    playerId, enabled, status.state(), status.lastError());
            return;
        }

        ItemStack ringStack = player.getItemInHand(hand);
        if (!(ringStack.getItem() instanceof LanternRingItem)) {
            return;
        }
        RingConfig config = RingValidation.sanitize(RingConfigs.ensureIdentity(ringStack));
        if (sessions.ringIdActiveElsewhere(playerId, config.ringId())) {
            config = config.withRingId(UUID.randomUUID());
            LOGGER.info("Reassigned a copied ring identity for {} to {}", playerId, config.ringId());
        }
        RingConfigs.set(ringStack, config);

        UUID token = sessions.activate(playerId, config.ringId(), RingConfigs.effectivePrompt(config));
        if (token == null) {
            return;
        }
        LOGGER.info("Ring session activated for {} (hand={}, ring={}).",
                playerId, hand, config.ringId());
        HologramService.get().spawn(currentServer, player, token, config);
        playRingSound(player, 1.0f);
        ChatPresenter.sendStatus(currentServer, playerId, ACTIVATED_MESSAGE);
        generateIntroduction(currentServer, playerId, token);
    }

    public void onChat(ServerPlayer player, ServerChatEvent event) {
        ConversationPipeline current = pipeline;
        MinecraftServer currentServer = server;
        if (current == null || currentServer == null || player == null || event.isCanceled()
                || !aiEnabled()) {
            return;
        }

        UUID playerId = player.getUUID();
        RingSessionManager sessions = RingSessionManager.get();
        RingSessionManager.Session session = sessions.session(playerId);
        boolean active = session != null;
        boolean holdingRing = isHoldingRing(player, session == null ? null : session.ringId());

        if (RingSessionRules.shouldDeactivate(active, holdingRing)) {
            deactivate(player, RING_REQUIRED_MESSAGE, 0.8f);
            return;
        }
        if (!RingSessionRules.shouldRoute(true, active, holdingRing)) {
            return;
        }

        String text = event.getRawText().strip().replace('\u00a7', '&');
        if (text.isEmpty()) {
            return;
        }

        UUID token = session == null ? null : session.token();
        event.setCanceled(true);
        ChatPresenter.sendPlayerMessage(currentServer, player, text);
        current.onPlayerMessage(playerId, text, token);
    }

    public void onEquipmentChanged(ServerPlayer player) {
        if (player == null || server == null) {
            return;
        }
        RingSessionManager.Session session = RingSessionManager.get().session(player.getUUID());
        if (session != null && !isHoldingRing(player, session.ringId())) {
            deactivate(player, RING_REQUIRED_MESSAGE, 0.8f);
        }
    }

    public void onRingConfigSaved(ServerPlayer player, RingConfig config) {
        if (player == null || config == null) {
            return;
        }
        UUID playerId = player.getUUID();
        RingSessionManager.Session session = RingSessionManager.get().session(playerId);
        if (session == null || session.ringId() == null || !session.ringId().equals(config.ringId())) {
            return;
        }
        RingSessionManager.get().updatePrompt(playerId, config.ringId(), RingConfigs.effectivePrompt(config));
        HologramService.get().updateConfig(playerId, config);
    }

    public void onPlayerLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        if (RingSessionManager.get().deactivate(playerId)) {
            HologramService.get().beginDespawn(playerId);
            LOGGER.info("Ring session for {} ended on logout.", playerId);
        }
    }

    public void onPlayerChangedDimension(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        if (RingSessionManager.get().deactivate(playerId)) {
            HologramService.get().beginDespawn(playerId);
            LOGGER.info("Ring session for {} ended on dimension change.", playerId);
        }
    }

    public void onPlayerDeath(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        if (RingSessionManager.get().deactivate(playerId)) {
            HologramService.get().beginDespawn(playerId);
            LOGGER.info("Ring session for {} ended on death.", playerId);
        }
    }

    private void deactivate(ServerPlayer player, String message, float pitch) {
        UUID playerId = player.getUUID();
        if (!RingSessionManager.get().deactivate(playerId)) {
            return;
        }
        HologramService.get().beginDespawn(playerId);
        LOGGER.info("Ring session deactivated for {}.", playerId);
        playRingSound(player, pitch);
        ChatPresenter.sendStatus(server, playerId, message);
    }

    private void generateIntroduction(MinecraftServer currentServer, UUID playerId, UUID token) {
        ConversationPipeline current = pipeline;
        if (current == null) {
            return;
        }
        current.generateIntroduction(playerId).whenComplete((answer, error) -> {
            if (error != null) {
                LOGGER.warn("Could not generate the ring introduction for {}: {}",
                        playerId, AiService.rootMessage(error), error);
                return;
            }
            String text = answer == null ? "" : answer.strip();
            if (text.isEmpty()) {
                return;
            }
            try {
                currentServer.execute(() -> {
                    if (RingSessionManager.get().isCurrent(playerId, token)) {
                        ChatPresenter.sendDialogue(currentServer, playerId, text);
                    }
                });
            } catch (RejectedExecutionException e) {
                LOGGER.debug("Could not deliver the ring introduction because the server is stopping.", e);
            }
        });
    }

    private void playRingSound(ServerPlayer player, float pitch) {
        try {
            player.serverLevel().playSound(null, player.blockPosition(), ModSounds.RING_SOUND.get(),
                    SoundSource.PLAYERS, 0.8f, pitch);
        } catch (RuntimeException e) {
            LOGGER.debug("Could not play the ring sound for {}.", player.getUUID(), e);
        }
    }

    private static boolean isHoldingRing(ServerPlayer player, UUID ringId) {
        if (ringId == null) {
            return isHoldingRing(player);
        }
        return isRingWithId(player.getMainHandItem(), ringId)
                || isRingWithId(player.getOffhandItem(), ringId);
    }

    private static boolean isRingWithId(ItemStack stack, UUID ringId) {
        return stack.getItem() instanceof LanternRingItem
                && ringId.equals(RingConfigs.get(stack).ringId());
    }

    private static boolean isHoldingRing(ServerPlayer player) {
        return player.getMainHandItem().is(ModItems.LANTERN_RING.get())
                || player.getOffhandItem().is(ModItems.LANTERN_RING.get());
    }

    private static boolean aiEnabled() {
        try {
            return Config.AI_ENABLED.get();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private final class Sink implements ConversationPipeline.ChatSink {
        @Override
        public void sendDialogue(UUID playerId, String markdown) {
            ChatPresenter.sendDialogue(server, playerId, markdown);
        }

        @Override
        public void sendStatus(UUID playerId, String text) {
            ChatPresenter.sendStatus(server, playerId, text);
        }
    }
}
