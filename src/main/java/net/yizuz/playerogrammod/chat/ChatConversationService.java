package net.yizuz.playerogrammod.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.yizuz.playerogrammod.Config;
import net.yizuz.playerogrammod.ai.AiService;
import net.yizuz.playerogrammod.ai.AiState;
import net.yizuz.playerogrammod.prompt.SystemPromptProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChatConversationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatConversationService.class);
    private static final ChatConversationService INSTANCE = new ChatConversationService();
    private final AtomicBoolean readyListenerRegistered = new AtomicBoolean(false);

    private volatile MinecraftServer server;
    private volatile ConversationPipeline pipeline;
    private volatile String pendingGreeting;

    private ChatConversationService() {
    }

    public static ChatConversationService get() {
        return INSTANCE;
    }

    public void init() {
        if (readyListenerRegistered.compareAndSet(false, true)) {
            AiService.get().addReadyListener(this::onRuntimeReady);
        }
    }

    public void onServerStarting(MinecraftServer server) {
        this.server = server;
        this.pendingGreeting = null;
        SystemPromptProvider.instance().reload();

        int maxMessages = Config.HISTORY_MESSAGES.get();
        int maxChars = Config.HISTORY_MAX_CHARS.get();
        String prefix = Config.CHAT_PREFIX.get();
        ConversationPipeline newPipeline = new ConversationPipeline(
                new ConversationManager(maxMessages, maxChars),
                SystemPromptProvider.instance(),
                AiService.get()::complete,
                new Sink(),
                server::execute,
                prefix);
        newPipeline.resetGreeting();
        this.pipeline = newPipeline;
        LOGGER.info("AI chat pipeline ready (prefix '{}', history {} messages / {} chars).",
                newPipeline.chatPrefix(), maxMessages, maxChars);
    }

    public void onServerStarted() {
        if (server != null && AiService.get().status().state() == AiState.READY) {
            onRuntimeReady();
        }
    }

    public void onServerStopping() {
        ConversationPipeline current = pipeline;
        if (current != null) {
            current.clearConversations();
        }
        pipeline = null;
        server = null;
        pendingGreeting = null;
    }

    public void onChat(ServerPlayer player, ServerChatEvent event) {
        ConversationPipeline current = pipeline;
        if (current == null || player == null || server == null || event.isCanceled() || !aiEnabled()) {
            return;
        }

        String prompt = current.extractPrompt(event.getRawText());
        if (prompt == null) {
            return;
        }

        String clean = prompt.replace('\u00a7', '&');
        event.setMessage(Component.literal(clean));
        current.onPlayerMessage(player.getUUID(), clean);
    }

    public void onPlayerJoin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        String pending = pendingGreeting;
        if (pending == null) {
            return;
        }
        pendingGreeting = null;
        MinecraftServer current = server;
        if (current != null) {
            ChatPresenter.broadcast(current, pending);
        }
    }

    private void onRuntimeReady() {
        MinecraftServer current = server;
        ConversationPipeline currentPipeline = pipeline;
        if (current == null || currentPipeline == null || !aiEnabled()) {
            return;
        }

        currentPipeline.greetOnce().whenComplete((answer, error) -> {
            if (error != null) {
                LOGGER.warn("Could not generate the AI greeting: {}", AiService.rootMessage(error), error);
                return;
            }
            String text = answer == null ? "" : answer.strip();
            if (text.isEmpty()) {
                return;
            }
            try {
                current.execute(() -> {
                    if (current.getPlayerList().getPlayerCount() > 0) {
                        ChatPresenter.broadcast(current, text);
                    } else {
                        pendingGreeting = text;
                    }
                });
            } catch (RejectedExecutionException e) {
                LOGGER.debug("Could not deliver the AI greeting because the server is stopping.", e);
            }
        });
    }

    public void clearConversation(UUID playerId) {
        ConversationPipeline current = pipeline;
        if (current != null) {
            current.clearConversation(playerId);
        }
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
        public void broadcastDialogue(String markdown) {
            ChatPresenter.broadcast(server, markdown);
        }

        @Override
        public void sendStatus(UUID playerId, String text) {
            ChatPresenter.sendStatus(server, playerId, text);
        }
    }
}
