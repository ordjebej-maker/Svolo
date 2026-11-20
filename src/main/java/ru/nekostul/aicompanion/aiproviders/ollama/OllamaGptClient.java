package ru.nekostul.aicompanion.aiproviders.ollama;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import ru.nekostul.aicompanion.CompanionConfig;
import ru.nekostul.aicompanion.aiproviders.yandexgpt.YandexGptClient;
import ru.nekostul.aicompanion.aiproviders.yandexgpt.YandexGptConversationMemory;
import ru.nekostul.aicompanion.aiproviders.yandexgpt.YandexGptPrompts;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Local LLM provider backed by the Ollama HTTP API.
 *
 * All HTTP requests are made by callers on a worker thread, never on the
 * Minecraft server tick thread.
 */
public final class OllamaGptClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_REPLY_LENGTH = 256;
    private static final int BUILD_PLAN_MIN_TOKENS = 2048;

    private OllamaGptClient() {
    }

    public static YandexGptClient.Result ask(ServerPlayer player, String playerMessage) {
        if (player == null || !isNotBlank(playerMessage)) {
            return error();
        }

        UUID playerId = player.getUUID();
        String userPrompt = YandexGptPrompts.userPrompt(
                player.getName().getString(),
                playerMessage
        );
        YandexGptClient.Result result = requestWithPrompts(
                YandexGptPrompts.system(),
                userPrompt,
                CompanionConfig.getAiMaxTokens(),
                true,
                YandexGptConversationMemory.snapshot(playerId)
        );

        if (result.status() == YandexGptClient.Status.SUCCESS && isNotBlank(result.text())) {
            YandexGptConversationMemory.appendUser(playerId, userPrompt);
            YandexGptConversationMemory.appendAssistant(playerId, result.text());
        }
        return result;
    }

    public static YandexGptClient.Result interpretCommand(ServerPlayer player, String playerMessage) {
        if (player == null || !isNotBlank(playerMessage)) {
            return error();
        }
        return requestWithPrompts(
                YandexGptPrompts.commandSystemPrompt(),
                YandexGptPrompts.commandUserPrompt(player.getName().getString(), playerMessage),
                CompanionConfig.getAiMaxTokens(),
                true,
                List.of()
        );
    }

    public static YandexGptClient.Result reviewHomeAssessment(ServerPlayer player, String assessmentPayload) {
        if (player == null || !isNotBlank(assessmentPayload)) {
            return error();
        }
        return requestWithPrompts(
                YandexGptPrompts.homeReviewSystemPrompt(),
                YandexGptPrompts.homeReviewUserPrompt(player.getName().getString(), assessmentPayload),
                CompanionConfig.getAiMaxTokens(),
                true,
                List.of()
        );
    }

    public static YandexGptClient.Result generateHomeBuildPlan(
            ServerPlayer player,
            String buildRequest,
            String buildPointContext
    ) {
        if (player == null || !isNotBlank(buildRequest)) {
            return error();
        }
        int maxTokens = Math.max(CompanionConfig.getAiMaxTokens(), BUILD_PLAN_MIN_TOKENS);
        return requestWithPrompts(
                YandexGptPrompts.homeBuildSystemPrompt(),
                YandexGptPrompts.homeBuildUserPrompt(
                        player.getName().getString(),
                        buildRequest,
                        buildPointContext
                ),
                maxTokens,
                false,
                List.of()
        );
    }

    private static YandexGptClient.Result requestWithPrompts(
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            boolean shortTextMode,
            List<YandexGptConversationMemory.Message> historyMessages
    ) {
        String baseUrl = CompanionConfig.getOllamaBaseUrl();
        String model = CompanionConfig.getOllamaModel();
        if (!isNotBlank(baseUrl) || !isNotBlank(model)
                || !isNotBlank(systemPrompt) || !isNotBlank(userPrompt)) {
            return new YandexGptClient.Result(YandexGptClient.Status.NOT_CONFIGURED, "", 0);
        }

        try {
            String answer = requestCompletion(
                    baseUrl,
                    model,
                    systemPrompt,
                    userPrompt,
                    maxTokens,
                    historyMessages
            );
            if (!isNotBlank(answer)) {
                return error();
            }

            String finalText = shortTextMode
                    ? sanitizeReply(answer)
                    : sanitizeStructuredReply(answer);
            if (!isNotBlank(finalText)) {
                return error();
            }
            return new YandexGptClient.Result(YandexGptClient.Status.SUCCESS, finalText, 0);
        } catch (Exception exception) {
            LOGGER.debug("Ollama request failed: {}", exception.toString());
            return error();
        }
    }

    private static String requestCompletion(
            String baseUrl,
            String model,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            List<YandexGptConversationMemory.Message> historyMessages
    ) throws Exception {
        String normalizedBaseUrl = baseUrl.trim();
        while (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        String endpoint = normalizedBaseUrl.endsWith("/api/chat")
                ? normalizedBaseUrl
                : normalizedBaseUrl + "/api/chat";

        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(CompanionConfig.getOllamaConnectTimeoutMs());
            connection.setReadTimeout(CompanionConfig.getOllamaReadTimeoutMs());
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");

            JsonObject body = new JsonObject();
            body.addProperty("model", model.trim());
            body.addProperty("stream", false);
            body.addProperty("think", false);
            body.addProperty("keep_alive", "5m");

            JsonObject options = new JsonObject();
            options.addProperty("temperature", CompanionConfig.getAiTemperature());
            options.addProperty("num_predict", Math.max(16, maxTokens));
            body.add("options", options);

            JsonArray messages = new JsonArray();
            appendMessage(messages, "system", systemPrompt);
            if (historyMessages != null) {
                for (YandexGptConversationMemory.Message historyMessage : historyMessages) {
                    if (historyMessage != null) {
                        appendMessage(messages, historyMessage.role(), historyMessage.text());
                    }
                }
            }
            appendMessage(messages, "user", userPrompt);
            body.add("messages", messages);

            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 400
                    ? connection.getErrorStream()
                    : connection.getInputStream();
            String responseText = "";
            if (stream != null) {
                try (InputStream input = stream) {
                    responseText = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            if (responseCode >= 400) {
                LOGGER.debug("Ollama returned HTTP status {}", responseCode);
                return null;
            }
            if (responseText.isBlank()) {
                return null;
            }

            JsonObject root = JsonParser.parseString(responseText).getAsJsonObject();
            JsonObject message = root.getAsJsonObject("message");
            if (message == null || !message.has("content") || message.get("content").isJsonNull()) {
                return null;
            }
            return message.get("content").getAsString();
        } finally {
            connection.disconnect();
        }
    }

    private static void appendMessage(JsonArray messages, String role, String content) {
        if (messages == null || !isNotBlank(role) || !isNotBlank(content)) {
            return;
        }
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        messages.add(message);
    }

    private static String sanitizeReply(String raw) {
        String cleaned = stripThinking(raw)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (cleaned.length() <= MAX_REPLY_LENGTH) {
            return cleaned;
        }
        return cleaned.substring(0, MAX_REPLY_LENGTH).trim();
    }

    private static String sanitizeStructuredReply(String raw) {
        return stripThinking(raw).replace(String.valueOf((char) 0), "").trim();
    }

    private static String stripThinking(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("(?s)<think>.*?</think>", "").trim();
    }

    private static YandexGptClient.Result error() {
        return new YandexGptClient.Result(YandexGptClient.Status.ERROR, "", 0);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
