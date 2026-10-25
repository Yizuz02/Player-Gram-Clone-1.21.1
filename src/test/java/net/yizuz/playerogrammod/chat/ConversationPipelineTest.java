package net.yizuz.playerogrammod.chat;

import net.yizuz.playerogrammod.ai.LlamaClient;
import net.yizuz.playerogrammod.prompt.SystemPromptProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationPipelineTest {
    private final UUID player = UUID.randomUUID();
    private final UUID token = UUID.randomUUID();
    private FakeCompletions completions;
    private RecordingSink sink;
    private ConversationManager manager;
    private SystemPromptProvider prompts;
    private ConversationPipeline pipeline;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        prompts = new SystemPromptProvider(tempDir.resolve("system_prompt.md"));
        prompts.reload();
        completions = new FakeCompletions();
        sink = new RecordingSink();
        manager = new ConversationManager(12, 6000);
        pipeline = new ConversationPipeline(manager, prompts, completions, sink, Runnable::run,
                (playerId, sessionToken) -> true);
    }

    @Test
    void buildsSystemPromptHistoryAndUserMessage() {
        completions.respond("hi");
        completions.respond("again-answer");

        pipeline.onPlayerMessage(player, "hello", token);
        pipeline.onPlayerMessage(player, "again", token);

        assertEquals(2, completions.requests.size());
        List<LlamaClient.Message> first = completions.requests.get(0);
        assertEquals(List.of("system", "user"), roles(first));
        assertEquals(prompts.get(), first.get(0).content());
        assertEquals("hello", first.get(1).content());

        List<LlamaClient.Message> second = completions.requests.get(1);
        assertEquals(List.of("system", "user", "assistant", "user"), roles(second));
        assertEquals("hello", second.get(1).content());
        assertEquals("hi", second.get(2).content());
        assertEquals("again", second.get(3).content());

        assertEquals(List.of(
                new RecordingSink.Dialogue(player, "hi"),
                new RecordingSink.Dialogue(player, "again-answer")), sink.dialogues);
        assertEquals(4, manager.get(player).messageCount());
        assertFalse(manager.get(player).isBusy());
    }

    @Test
    void busyConversationRejectsAConcurrentMessage() {
        CompletableFuture<String> pending = new CompletableFuture<>();
        completions.respond(pending);

        pipeline.onPlayerMessage(player, "one", token);
        pipeline.onPlayerMessage(player, "two", token);

        assertEquals(1, completions.requests.size());
        assertTrue(statusTexts().contains(ConversationPipeline.BUSY_STATUS));
        assertTrue(sink.dialogues.isEmpty());
        assertTrue(manager.get(player).isBusy());
    }

    @Test
    void slowInferenceReturnsImmediatelyAndDeliversLater() {
        CompletableFuture<String> pending = new CompletableFuture<>();
        completions.respond(pending);

        pipeline.onPlayerMessage(player, "hi", token);

        assertTrue(sink.dialogues.isEmpty());
        assertTrue(manager.get(player).isBusy());

        pending.complete("late answer");

        assertEquals(List.of(new RecordingSink.Dialogue(player, "late answer")), sink.dialogues);
        assertFalse(manager.get(player).isBusy());
    }

    @Test
    void failureClearsBusyAndDoesNotLeakDetails() {
        completions.respondFailed(new RuntimeException("HTTP 500 secret"));

        pipeline.onPlayerMessage(player, "boom", token);

        assertTrue(sink.dialogues.isEmpty());
        assertEquals(0, manager.get(player).messageCount());
        assertFalse(manager.get(player).isBusy());
        assertTrue(statusTexts().contains(ConversationPipeline.ERROR_STATUS));
        assertFalse(statusTexts().stream().anyMatch(text -> text.contains("secret")));

        completions.respond("recovered");
        pipeline.onPlayerMessage(player, "retry", token);
        assertEquals(List.of(new RecordingSink.Dialogue(player, "recovered")), sink.dialogues);
    }

    @Test
    void emptyAnswerIsTreatedAsFailure() {
        completions.respond("");

        pipeline.onPlayerMessage(player, "hi", token);

        assertTrue(sink.dialogues.isEmpty());
        assertEquals(0, manager.get(player).messageCount());
        assertFalse(manager.get(player).isBusy());
        assertTrue(statusTexts().contains(ConversationPipeline.ERROR_STATUS));
    }

    @Test
    void synchronousCompletionFailureClearsBusy() {
        ConversationPipeline broken = new ConversationPipeline(manager, prompts, request -> {
            throw new RuntimeException("boom");
        }, sink, Runnable::run, (playerId, sessionToken) -> true);

        broken.onPlayerMessage(player, "hi", token);

        assertTrue(sink.dialogues.isEmpty());
        assertEquals(0, manager.get(player).messageCount());
        assertFalse(manager.get(player).isBusy());
        assertTrue(statusTexts().contains(ConversationPipeline.ERROR_STATUS));
    }

    @Test
    void playersKeepSeparateHistories() {
        UUID other = UUID.randomUUID();
        completions.respond("a1");
        completions.respond("b1");

        pipeline.onPlayerMessage(player, "one", token);
        pipeline.onPlayerMessage(other, "two", token);

        assertEquals(2, completions.requests.size());
        List<LlamaClient.Message> secondRequest = completions.requests.get(1);
        assertEquals(List.of("system", "user"), roles(secondRequest));
        assertEquals("two", secondRequest.get(1).content());
        assertEquals(2, manager.get(player).messageCount());
        assertEquals(2, manager.get(other).messageCount());
    }

    @Test
    void introductionIsGeneratedWithoutHistory() {
        completions.respond("Hello players");

        assertEquals("Hello players", pipeline.generateIntroduction().join());
        assertEquals(1, completions.requests.size());
        List<LlamaClient.Message> request = completions.requests.get(0);
        assertEquals(List.of("system", "user"), roles(request));
        assertEquals(prompts.get(), request.get(0).content());
        assertEquals(ConversationPipeline.INTRODUCTION_INSTRUCTION, request.get(1).content());
        assertEquals(0, manager.size());

        completions.respond("Hello again");
        assertEquals("Hello again", pipeline.generateIntroduction().join());
        assertEquals(2, completions.requests.size());
    }

    @Test
    void usesPlayerSpecificPromptForMessagesAndIntroduction() {
        ConversationPipeline ringPipeline = new ConversationPipeline(manager, prompts, completions,
                sink, Runnable::run, (playerId, sessionToken) -> true,
                playerId -> playerId.equals(player) ? "ring prompt" : null);

        completions.respond("hello back");
        ringPipeline.onPlayerMessage(player, "hi", token);
        assertEquals("ring prompt", completions.requests.get(0).get(0).content());

        completions.respond("intro");
        assertEquals("intro", ringPipeline.generateIntroduction(player).join());
        assertEquals("ring prompt", completions.requests.get(1).get(0).content());

        UUID other = UUID.randomUUID();
        completions.respond("other answer");
        ringPipeline.onPlayerMessage(other, "hi", token);
        assertEquals(prompts.get(), completions.requests.get(2).get(0).content());
    }

    @Test
    void endedSessionSuppressesResponseAndHistory() {
        AtomicReference<UUID> currentToken = new AtomicReference<>(token);
        ConversationPipeline sessionPipeline = new ConversationPipeline(manager, prompts, completions,
                sink, Runnable::run,
                (playerId, sessionToken) -> Objects.equals(currentToken.get(), sessionToken));

        CompletableFuture<String> pending = new CompletableFuture<>();
        completions.respond(pending);

        sessionPipeline.onPlayerMessage(player, "hi", token);
        currentToken.set(UUID.randomUUID());
        pending.complete("late answer");

        assertTrue(sink.dialogues.isEmpty());
        assertEquals(0, manager.get(player).messageCount());
        assertFalse(manager.get(player).isBusy());
    }

    @Test
    void inactiveSessionDoesNotStartARequest() {
        ConversationPipeline inactive = new ConversationPipeline(manager, prompts, completions, sink,
                Runnable::run, (playerId, sessionToken) -> false);

        inactive.onPlayerMessage(player, "hi", token);

        assertTrue(completions.requests.isEmpty());
        assertTrue(sink.dialogues.isEmpty());
        assertTrue(sink.statuses.isEmpty());
    }

    private List<String> statusTexts() {
        return sink.statuses.stream().map(RecordingSink.Status::text).toList();
    }

    private static List<String> roles(List<LlamaClient.Message> messages) {
        return messages.stream().map(LlamaClient.Message::role).toList();
    }

    private static final class FakeCompletions
            implements Function<List<LlamaClient.Message>, CompletableFuture<String>> {
        private final List<List<LlamaClient.Message>> requests = new ArrayList<>();
        private final Deque<CompletableFuture<String>> responses = new ArrayDeque<>();

        @Override
        public CompletableFuture<String> apply(List<LlamaClient.Message> request) {
            requests.add(List.copyOf(request));
            return responses.isEmpty() ? CompletableFuture.completedFuture("ok") : responses.removeFirst();
        }

        void respond(String answer) {
            responses.add(CompletableFuture.completedFuture(answer));
        }

        void respond(CompletableFuture<String> future) {
            responses.add(future);
        }

        void respondFailed(Throwable error) {
            responses.add(CompletableFuture.failedFuture(error));
        }
    }

    private static final class RecordingSink implements ConversationPipeline.ChatSink {
        private final List<Dialogue> dialogues = new ArrayList<>();
        private final List<Status> statuses = new ArrayList<>();

        @Override
        public void sendDialogue(UUID playerId, String markdown) {
            dialogues.add(new Dialogue(playerId, markdown));
        }

        @Override
        public void sendStatus(UUID playerId, String text) {
            statuses.add(new Status(playerId, text));
        }

        private record Dialogue(UUID playerId, String markdown) {
        }

        private record Status(UUID playerId, String text) {
        }
    }
}
