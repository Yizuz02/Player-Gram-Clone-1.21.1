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
import java.util.function.BiPredicate;
import java.util.function.Function;

public final class ConversationPipeline {
    public interface ChatSink {
        void sendDialogue(UUID playerId, String markdown);

        void sendStatus(UUID playerId, String text);
    }

    public static final String BUSY_STATUS = "I'm still thinking about your previous message.";
    public static final String ERROR_STATUS =
            "I couldn't answer that just now. Check the server console for details.";
    public static final String INTRODUCTION_INSTRUCTION =
            "You have just been connected to a player through the Lantern Ring. "
                    + "Greet them with one short, natural sentence. Do not use Markdown, lists, or headings.";

    private static final Logger LOGGER = LoggerFactory.getLogger(ConversationPipeline.class);

    private final ConversationManager conversations;
    private final SystemPromptProvider prompts;
    private final Function<List<LlamaClient.Message>, CompletableFuture<String>> completions;
    private final ChatSink sink;
    private final Executor serverExecutor;
    private final BiPredicate<UUID, UUID> sessionCurrent;
    private final Function<UUID, String> promptFor;

    public ConversationPipeline(ConversationManager conversations, SystemPromptProvider prompts,
                                Function<List<LlamaClient.Message>, CompletableFuture<String>> completions,
                                ChatSink sink, Executor serverExecutor,
                                BiPredicate<UUID, UUID> sessionCurrent) {
        this(conversations, prompts, completions, sink, serverExecutor, sessionCurrent,
                ignored -> prompts.get());
    }

    public ConversationPipeline(ConversationManager conversations, SystemPromptProvider prompts,
                                Function<List<LlamaClient.Message>, CompletableFuture<String>> completions,
                                ChatSink sink, Executor serverExecutor,
                                BiPredicate<UUID, UUID> sessionCurrent,
                                Function<UUID, String> promptFor) {
        this.conversations = Objects.requireNonNull(conversations, "conversations");
        this.prompts = Objects.requireNonNull(prompts, "prompts");
        this.completions = Objects.requireNonNull(completions, "completions");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.serverExecutor = Objects.requireNonNull(serverExecutor, "serverExecutor");
        this.sessionCurrent = Objects.requireNonNull(sessionCurrent, "sessionCurrent");
        this.promptFor = Objects.requireNonNull(promptFor, "promptFor");
    }

    public void onPlayerMessage(UUID playerId, String prompt, UUID sessionToken) {
        if (playerId == null || prompt == null || prompt.isBlank()
                || !sessionCurrent.test(playerId, sessionToken)) {
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
            request.add(new LlamaClient.Message("system", systemPrompt(playerId)));
            request.addAll(conversation.snapshot());
            request.add(new LlamaClient.Message("user", prompt));
            future = completions.apply(request);
        } catch (RuntimeException e) {
            conversation.end();
            handleFailure(playerId, sessionToken, e);
            return;
        }

        if (future == null) {
            conversation.end();
            handleFailure(playerId, sessionToken,
                    new IllegalStateException("No completion future was returned."));
            return;
        }

        future.whenComplete((answer, error) -> {
            try {
                serverExecutor.execute(() ->
                        deliver(playerId, prompt, conversation, sessionToken, answer, error));
            } catch (RejectedExecutionException e) {
                conversation.end();
                LOGGER.warn("Could not deliver the AI response because the server is stopping.", e);
            }
        });
    }

    public CompletableFuture<String> generateIntroduction() {
        return generateIntroduction(null);
    }

    public CompletableFuture<String> generateIntroduction(UUID playerId) {
        CompletableFuture<String> future;
        try {
            String system = playerId == null ? prompts.get() : systemPrompt(playerId);
            List<LlamaClient.Message> request = List.of(
                    new LlamaClient.Message("system", system),
                    new LlamaClient.Message("user", INTRODUCTION_INSTRUCTION));
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

    private String systemPrompt(UUID playerId) {
        String prompt = promptFor.apply(playerId);
        return prompt == null ? prompts.get() : prompt;
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
                         UUID sessionToken, String answer, Throwable error) {
        try {
            conversation.end();
            if (!sessionCurrent.test(playerId, sessionToken)) {
                LOGGER.debug("Suppressing AI response for an ended or replaced ring session ({}).", playerId);
                return;
            }
            if (error != null) {
                handleFailure(playerId, sessionToken, error);
                return;
            }
            String text = answer == null ? "" : answer.strip();
            if (text.isEmpty()) {
                handleFailure(playerId, sessionToken,
                        new IllegalStateException("The model returned an empty response."));
                return;
            }
            conversation.addExchange(prompt, text);
            sink.sendDialogue(playerId, text);
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

    private void handleFailure(UUID playerId, UUID sessionToken, Throwable error) {
        LOGGER.warn("AI chat request failed for {}: {}", playerId, rootMessage(error), error);
        if (sessionCurrent.test(playerId, sessionToken)) {
            sendStatus(playerId, ERROR_STATUS);
        }
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
