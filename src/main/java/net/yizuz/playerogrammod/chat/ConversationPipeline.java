package net.yizuz.playerogrammod.chat;

import net.yizuz.playerogrammod.ai.LlamaClient;
import net.yizuz.playerogrammod.prompt.SystemPromptProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public final class ConversationPipeline {
    public interface ChatSink {
        void broadcastDialogue(String markdown);

        void sendStatus(UUID playerId, String text);
    }

    public static final String BUSY_STATUS = "I'm still thinking about your previous message.";
    public static final String ERROR_STATUS =
            "I couldn't answer that just now. Check the server console for details.";
    public static final String GREETING_INSTRUCTION =
            "You have just come online in the server chat. Greet the players with one short, natural sentence. "
                    + "Do not use Markdown, lists, or headings.";

    private static final Logger LOGGER = LoggerFactory.getLogger(ConversationPipeline.class);

    private final ConversationManager conversations;
    private final SystemPromptProvider prompts;
    private final Function<List<LlamaClient.Message>, CompletableFuture<String>> completions;
    private final ChatSink sink;
    private final Executor serverExecutor;
    private final String chatPrefix;
    private final AtomicBoolean greetingAttempted = new AtomicBoolean(false);

    public ConversationPipeline(ConversationManager conversations, SystemPromptProvider prompts,
                                Function<List<LlamaClient.Message>, CompletableFuture<String>> completions,
                                ChatSink sink, Executor serverExecutor, String chatPrefix) {
        this.conversations = Objects.requireNonNull(conversations, "conversations");
        this.prompts = Objects.requireNonNull(prompts, "prompts");
        this.completions = Objects.requireNonNull(completions, "completions");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.serverExecutor = Objects.requireNonNull(serverExecutor, "serverExecutor");
        this.chatPrefix = chatPrefix == null || chatPrefix.isBlank() ? "AI:" : chatPrefix.trim();
    }

    public String extractPrompt(String rawText) {
        return ChatRouter.extract(chatPrefix, rawText).orElse(null);
    }

    public String chatPrefix() {
        return chatPrefix;
    }

    public void onPlayerMessage(UUID playerId, String prompt) {
        if (playerId == null || prompt == null || prompt.isBlank()) {
            return;
        }

        ConversationManager.Conversation conversation = conversations.get(playerId);
        if (!conversation.tryBegin()) {
            sendStatus(playerId, BUSY_STATUS);
            return;
        }

        List<LlamaClient.Message> request = new ArrayList<>();
        CompletableFuture<String> future;
        try {
            request.add(new LlamaClient.Message("system", prompts.get()));
            request.addAll(conversation.snapshot());
            request.add(new LlamaClient.Message("user", prompt));
            future = completions.apply(request);
        } catch (RuntimeException e) {
            conversation.end();
            handleFailure(playerId, e);
            return;
        }

        if (future == null) {
            conversation.end();
            handleFailure(playerId, new IllegalStateException("No completion future was returned."));
            return;
        }

        future.whenComplete((answer, error) -> {
            try {
                serverExecutor.execute(() -> deliver(playerId, prompt, conversation, answer, error));
            } catch (RejectedExecutionException e) {
                conversation.end();
                LOGGER.warn("Could not deliver the AI response because the server is stopping.", e);
            }
        });
    }

    public CompletableFuture<String> greetOnce() {
        if (!greetingAttempted.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<String> future;
        try {
            List<LlamaClient.Message> request = List.of(
                    new LlamaClient.Message("system", prompts.get()),
                    new LlamaClient.Message("user", GREETING_INSTRUCTION));
            future = completions.apply(request);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }

        if (future == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("No completion future was returned."));
        }
        return future.thenApply(answer -> answer == null ? "" : answer.strip());
    }

    public void resetGreeting() {
        greetingAttempted.set(false);
    }

    public void clearConversation(UUID playerId) {
        if (playerId != null) {
            conversations.clear(playerId);
        }
    }

    public void clearConversations() {
        conversations.clearAll();
    }

    private void deliver(UUID playerId, String prompt, ConversationManager.Conversation conversation,
                         String answer, Throwable error) {
        try {
            conversation.end();
            if (error != null) {
                handleFailure(playerId, error);
                return;
            }
            String text = answer == null ? "" : answer.strip();
            if (text.isEmpty()) {
                handleFailure(playerId, new IllegalStateException("The model returned an empty response."));
                return;
            }
            conversation.addExchange(prompt, text);
            sink.broadcastDialogue(text);
        } catch (RuntimeException e) {
            LOGGER.warn("Could not deliver the AI response for {}.", playerId, e);
            sendStatus(playerId, ERROR_STATUS);
        } finally {
            conversation.end();
        }
    }

    private boolean sendStatus(UUID playerId, String text) {
        try {
            sink.sendStatus(playerId, text);
            return true;
        } catch (RuntimeException e) {
            LOGGER.warn("Could not send an AI status to {}.", playerId, e);
            return false;
        }
    }

    private void handleFailure(UUID playerId, Throwable error) {
        LOGGER.warn("AI chat request failed for {}: {}", playerId, rootMessage(error), error);
        sendStatus(playerId, ERROR_STATUS);
    }

    static String rootMessage(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
