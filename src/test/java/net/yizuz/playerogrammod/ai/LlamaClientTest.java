package net.yizuz.playerogrammod.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlamaClientTest {
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private volatile String responseBody = "{}";
    private HttpServer server;
    private int port;
    private LlamaClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        port = server.getAddress().getPort();
        client = new LlamaClient(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build());
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void disablesThinkingAndReturnsContent() {
        responseBody = """
                {"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"hola"}}]}
                """;

        String answer = ask(false);

        assertEquals("hola", answer);
        JsonObject request = JsonParser.parseString(lastRequestBody.get()).getAsJsonObject();
        assertFalse(request.getAsJsonObject("chat_template_kwargs").get("enable_thinking").getAsBoolean());
        assertEquals("none", request.get("reasoning_effort").getAsString());
        assertEquals(64, request.get("max_tokens").getAsInt());
    }

    @Test
    void enablesThinkingWhenRequested() {
        responseBody = """
                {"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"ok"}}]}
                """;

        assertEquals("ok", ask(true));

        JsonObject request = JsonParser.parseString(lastRequestBody.get()).getAsJsonObject();
        assertTrue(request.getAsJsonObject("chat_template_kwargs").get("enable_thinking").getAsBoolean());
        assertFalse(request.has("reasoning_effort"));
    }

    @Test
    void explainsTruncatedReasoning() {
        responseBody = """
                {"choices":[{"finish_reason":"length","message":{"role":"assistant","content":"",
                "reasoning_content":"Thinking Process: this went on too long"}}]}
                """;

        AiException error = assertThrows(AiException.class, () -> ask(false));

        assertTrue(error.getMessage().contains("reasoning"));
        assertTrue(error.getMessage().contains("maxTokens"));
    }

    @Test
    void fallsBackToReasoningWhenNotTruncated() {
        responseBody = """
                {"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"",
                "reasoning_content":"The answer is 42."}}]}
                """;

        assertEquals("The answer is 42.", ask(false));
    }

    @Test
    void acceptsContentAsParts() {
        responseBody = """
                {"choices":[{"finish_reason":"stop","message":{"role":"assistant",
                "content":[{"type":"text","text":"hola"},{"type":"text","text":" mundo"}]}}]}
                """;

        assertEquals("hola mundo", ask(false));
    }

    @Test
    void explainsEmptyTruncatedAnswer() {
        responseBody = """
                {"choices":[{"finish_reason":"length","message":{"role":"assistant","content":""}}]}
                """;

        AiException error = assertThrows(AiException.class, () -> ask(false));

        assertTrue(error.getMessage().contains("maxTokens"));
    }

    private String ask(boolean enableThinking) {
        return client.chat("127.0.0.1", port, "test-key",
                List.of(new LlamaClient.Message("user", "hi")),
                0.7, 64, enableThinking, Duration.ofSeconds(5));
    }
}
