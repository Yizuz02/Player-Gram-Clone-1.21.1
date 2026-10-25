package net.yizuz.playerogrammod.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import net.yizuz.playerogrammod.ai.LlamaClient;
import net.yizuz.playerogrammod.prompt.SystemPromptProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChatIntegrationTest {
    private final List<String> requestBodies = new CopyOnWriteArrayList<>();
    private final AtomicInteger answers = new AtomicInteger();
    private HttpServer server;
    private RecordingSink sink;
    private ConversationPipeline pipeline;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String content = "answer-" + answers.incrementAndGet();
            String body = """
                    {"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"%s"}}]}
                    """.formatted(content);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();

        SystemPromptProvider prompts = new SystemPromptProvider(tempDir.resolve("prompt.md"));
        prompts.reload();
        sink = new RecordingSink();
        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        LlamaClient client = new LlamaClient(http);
        pipeline = new ConversationPipeline(
                new ConversationManager(12, 6000),
                prompts,
                messages -> CompletableFuture.completedFuture(client.chat(
                        "127.0.0.1", server.getAddress().getPort(), "test-key",
                        messages, 0.7, 64, false, Duration.ofSeconds(5))),
                sink,
                Runnable::run,
                "AI:");
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void addressedChatFlowsThroughLlamaClientAndHistory() {
        UUID player = UUID.randomUUID();

        pipeline.onPlayerMessage(player, "hello");
        pipeline.onPlayerMessage(player, "again");

        assertEquals(List.of("answer-1", "answer-2"), sink.broadcasts);
        assertEquals(2, requestBodies.size());

        JsonObject first = JsonParser.parseString(requestBodies.get(0)).getAsJsonObject();
        JsonArray firstMessages = first.getAsJsonArray("messages");
        assertEquals("system", firstMessages.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("user", firstMessages.get(1).getAsJsonObject().get("role").getAsString());
        assertEquals("hello", firstMessages.get(1).getAsJsonObject().get("content").getAsString());

        JsonObject second = JsonParser.parseString(requestBodies.get(1)).getAsJsonObject();
        JsonArray secondMessages = second.getAsJsonArray("messages");
        assertEquals(4, secondMessages.size());
        assertEquals("user", secondMessages.get(1).getAsJsonObject().get("role").getAsString());
        assertEquals("hello", secondMessages.get(1).getAsJsonObject().get("content").getAsString());
        assertEquals("assistant", secondMessages.get(2).getAsJsonObject().get("role").getAsString());
        assertEquals("answer-1", secondMessages.get(2).getAsJsonObject().get("content").getAsString());
        assertEquals("user", secondMessages.get(3).getAsJsonObject().get("role").getAsString());
        assertEquals("again", secondMessages.get(3).getAsJsonObject().get("content").getAsString());
        assertFalse(second.getAsJsonObject("chat_template_kwargs").get("enable_thinking").getAsBoolean());
    }

    private record Status(UUID playerId, String text) {
    }

    private static final class RecordingSink implements ConversationPipeline.ChatSink {
        private final List<String> broadcasts = new CopyOnWriteArrayList<>();
        private final List<Status> statuses = new CopyOnWriteArrayList<>();

        @Override
        public void broadcastDialogue(String markdown) {
            broadcasts.add(markdown);
        }

        @Override
        public void sendStatus(UUID playerId, String text) {
            statuses.add(new Status(playerId, text));
        }
    }
}
