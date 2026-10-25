package net.yizuz.playerogrammod.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public final class LlamaClient {
    public record Message(String role, String content) {
    }

    private final HttpClient http;

    public LlamaClient(HttpClient http) {
        this.http = http;
    }

    public boolean isHealthy(String host, int port) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl(host, port) + "/health"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public String chat(String host, int port, String apiKey, List<Message> messages, double temperature,
                       int maxTokens, boolean enableThinking, Duration timeout) {
        JsonObject body = new JsonObject();
        body.addProperty("model", "playerogram");
        body.addProperty("temperature", temperature);
        body.addProperty("max_tokens", maxTokens);
        body.addProperty("stream", false);
        body.addProperty("cache_prompt", true);
        JsonObject templateKwargs = new JsonObject();
        templateKwargs.addProperty("enable_thinking", enableThinking);
        body.add("chat_template_kwargs", templateKwargs);
        if (!enableThinking) {
            body.addProperty("reasoning_effort", "none");
        }

        JsonArray array = new JsonArray();
        for (Message message : messages) {
            JsonObject item = new JsonObject();
            item.addProperty("role", message.role());
            item.addProperty("content", message.content());
            array.add(item);
        }
        body.add("messages", array);

        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl(host, port) + "/v1/chat/completions"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        if (apiKey != null && !apiKey.isBlank()) {
            request.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new AiException("Could not reach llama-server: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("The request was interrupted.", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AiException("llama-server returned HTTP " + response.statusCode() + ": " + truncate(response.body()));
        }

        try {
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonElement choicesElement = json.get("choices");
            if (choicesElement == null || !choicesElement.isJsonArray() || choicesElement.getAsJsonArray().isEmpty()) {
                throw new AiException("llama-server returned an empty response: " + truncate(response.body()));
            }
            JsonObject choice = choicesElement.getAsJsonArray().get(0).getAsJsonObject();
            JsonObject message = choice.has("message") && choice.get("message").isJsonObject()
                    ? choice.getAsJsonObject("message") : null;
            String finishReason = choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()
                    ? choice.get("finish_reason").getAsString() : "";

            if (message != null) {
                String content = extractText(message.get("content"));
                if (!content.isBlank()) {
                    return content;
                }
                String reasoning = extractText(message.get("reasoning_content"));
                if (!reasoning.isBlank()) {
                    if ("length".equals(finishReason)) {
                        throw new AiException("The model used all " + maxTokens
                                + " tokens on reasoning before answering. Set enableThinking=false"
                                + " or increase maxTokens.");
                    }
                    return reasoning;
                }
            }
            String text = extractText(choice.get("text"));
            if (!text.isBlank()) {
                return text;
            }
            if ("length".equals(finishReason)) {
                throw new AiException("The model reached maxTokens (" + maxTokens
                        + ") before producing any text. Increase maxTokens.");
            }
            throw new AiException("llama-server returned no text: " + truncate(response.body()));
        } catch (AiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AiException("Could not parse the llama-server response: " + e.getMessage(), e);
        }
    }

    private static String extractText(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (element.isJsonArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonElement part : element.getAsJsonArray()) {
                text.append(extractText(part));
            }
            return text.toString();
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("text")) {
                return extractText(object.get("text"));
            }
            if (object.has("content")) {
                return extractText(object.get("content"));
            }
        }
        return "";
    }

    private static String baseUrl(String host, int port) {
        return "http://" + LlamaProcess.connectHost(host) + ":" + port;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        String text = value.replaceAll("\\s+", " ").trim();
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }
}
