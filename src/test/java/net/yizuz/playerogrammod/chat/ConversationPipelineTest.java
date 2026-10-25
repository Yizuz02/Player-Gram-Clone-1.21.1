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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationPipelineTest {
    private final UUID player = UUID.randomUUID();
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
        pipeline = new ConversationPipeline(manager, prompts, completions, sink, Runnable::run, "AI:");
    }

    @Test
    void buildsSystemPromptHistoryAndUserMessage() {
        completions.respond("hi");
        completions.respond("again-answer");

        pipeline.onPlayerMessage(player, "hello");
        pipeline.onPlayerMessage(player, "again");

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

        assertEquals(List.of("hi", "again-answer"), sink.broadcasts);
        assertEquals(4, manager.get(player).messageCount());
        assertFalse(manager.get(player).isBusy());
    }

    @Test
    void busyConversationRejectsAConcurrentMessage() {
        CompletableFuture<String> pending = new CompletableFuture<>();
        completions.respond(pending);

        pipeline.onPlayerMessage(player, "one");
        pipeline.onPlayerMessage(player, "two");

        assertEquals(1, completions.requests.size());
        assertTrue(statusTexts().contains(ConversationPipeline.BUSY_STATUS));
        assertTrue(sink.broadcasts.isEmpty());
        assertTrue(manager.get(player).isBusy());
    }

    @Test
    void slowInferenceReturnsImmediatelyAndDeliversLater() {
        CompletableFuture<String> pending = new CompletableFuture<>();
        completions.respond(pending);

        pipeline.onPlayerMessage(player, "hi");

        assertTrue(sink.broadcasts.isEmpty());
        assertTrue(manager.get(player).isBusy());

        pending.complete("late answer");

        assertEquals(List.of("late answer"), sink.broadcasts);
        assertFalse(manager.get(player).isBusy());
    }

    @Test
    void failureClearsBusyAndDoesNotLeakDetails() {
        completions.respondFailed(new RuntimeException("HTTP 500 secret"));

        pipeline.onPlayerMessage(player, "boom");

        assertTrue(sink.broadcasts.isEmpty());
        assertEquals(0, manager.get(player).messageCount());
        assertFalse(manager.get(player).isBusy());
        assertTrue(statusTexts().contains(ConversationPipeline.ERROR_STATUS));
        assertFalse(statusTexts().stream().anyMatch(text -> text.contains("secret")));

        completions.respond("recovered");
        pipeline.onPlayerMessage(player, "retry");
        assertEquals(List.of("recovered"), sink.broadcasts);
    }

    @Test
    void emptyAnswerIsTreatedAsFailure() {
        completions.respond("");

        pipeline.onPlayerMessage(player, "hi");

        assertTrue(sink.broadcasts.isEmpty());
        assertEquals(0, manager.get(player).messageCount());
        assertFalse(manager.get(player).isBusy());
        assertTrue(statusTexts().contains(ConversationPipeline.ERROR_STATUS));
    }

    @Test
    void synchronousCompletionFailureClearsBusy() {
        ConversationPipeline broken = new ConversationPipeline(manager, prompts, request -> {
            throw new RuntimeException("boom");
        }, sink, Runnable::run, "AI:");

        broken.onPlayerMessage(player, "hi");

        assertTrue(sink.broadcasts.isEmpty());
        assertEquals(0, manager.get(player).messageCount());
        assertFalse(manager.get(player).isBusy());
        assertTrue(statusTexts().contains(ConversationPipeline.ERROR_STATUS));
    }

    @Test
    void playersKeepSeparateHistories() {
        UUID other = UUID.randomUUID();
        completions.respond("a1");
        completions.respond("b1");

        pipeline.onPlayerMessage(player, "one");
        pipeline.onPlayerMessage(other, "two");

        assertEquals(2, completions.requests.size());
        List<LlamaClient.Message> secondRequest = completions.requests.get(1);
        assertEquals(List.of("system", "user"), roles(secondRequest));
        assertEquals("two", secondRequest.get(1).content());
        assertEquals(2, manager.get(player).messageCount());
        assertEquals(2, manager.get(other).messageCount());
    }

    @Test
    void greetingIsGeneratedOnceAndNeverStored() {
        completions.respond("Hello players");

        assertEquals("Hello players", pipeline.greetOnce().join());
        assertNull(pipeline.greetOnce().join());
        assertEquals(1, completions.requests.size());

        List<LlamaClient.Message> request = completions.requests.get(0);
        assertEquals(List.of("system", "user"), roles(request));
        assertEquals(prompts.get(), request.get(0).content());
        assertEquals(ConversationPipeline.GREETING_INSTRUCTION, request.get(1).content());
        assertEquals(0, manager.size());

        pipeline.resetGreeting();
        completions.respond("Hello again");
        assertEquals("Hello again", pipeline.greetOnce().join());
        assertEquals(2, completions.requests.size());
    }

    @Test
    void extractsTheConfiguredPrefix() {
        assertEquals("hi", pipeline.extractPrompt("AI: hi"));
        assertNull(pipeline.extractPrompt("hello"));
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
        private final List<String> broadcasts = new ArrayList<>();
        private final List<Status> statuses = new ArrayList<>();

        @Override
        public void broadcastDialogue(String markdown) {
            broadcasts.add(markdown);
        }

        @Override
        public void sendStatus(UUID playerId, String text) {
            statuses.add(new Status(playerId, text));
        }

        private record Status(UUID playerId, String text) {
        }
    }
}
