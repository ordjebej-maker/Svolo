package ru.nekostul.aicompanion.aiproviders;

import net.minecraft.server.level.ServerPlayer;
import ru.nekostul.aicompanion.CompanionConfig;
import ru.nekostul.aicompanion.aiproviders.ollama.OllamaGptClient;
import ru.nekostul.aicompanion.aiproviders.yandexgpt.YandexGptClient;

/**
 * Selects the configured language-model provider without coupling game logic
 * to any specific remote or local AI service.
 */
public final class AiProviderRouter {
    private AiProviderRouter() {
    }

    public static YandexGptClient.Result ask(ServerPlayer player, String message) {
        return useOllama()
                ? OllamaGptClient.ask(player, message)
                : YandexGptClient.ask(player, message);
    }

    public static YandexGptClient.Result interpretCommand(ServerPlayer player, String message) {
        return useOllama()
                ? OllamaGptClient.interpretCommand(player, message)
                : YandexGptClient.interpretCommand(player, message);
    }

    public static YandexGptClient.Result reviewHomeAssessment(ServerPlayer player, String payload) {
        return useOllama()
                ? OllamaGptClient.reviewHomeAssessment(player, payload)
                : YandexGptClient.reviewHomeAssessment(player, payload);
    }

    public static YandexGptClient.Result generateHomeBuildPlan(
            ServerPlayer player,
            String request,
            String buildPointContext
    ) {
        return useOllama()
                ? OllamaGptClient.generateHomeBuildPlan(player, request, buildPointContext)
                : YandexGptClient.generateHomeBuildPlan(player, request, buildPointContext);
    }

    private static boolean useOllama() {
        return "ollama".equalsIgnoreCase(CompanionConfig.getAiProvider());
    }
}
