package com.richie.component.ai.config;

import com.richie.component.ai.model.ModelOptions;
import com.richie.component.ai.support.AiChatOptionsResolver;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.ai.minimax.api.MiniMaxApi;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI ChatClient工厂
 * 统一负责根据配置构建各类模型客户端，支持配置文件与运行时动态初始化
 */
@Slf4j
@Component
public class AiChatClientFactory {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);
    private static final int DEFAULT_MAX_RETRIES = 3;

    private final AiChatOptionsResolver optionsResolver;
    private final ObservationRegistry observationRegistry;
    private final RetryTemplate retryTemplate;

    public AiChatClientFactory(AiChatOptionsResolver optionsResolver,
                               ObjectProvider<ObservationRegistry> observationRegistryProvider,
                               RetryTemplate retryTemplate) {
        this.optionsResolver = optionsResolver;
        this.observationRegistry = observationRegistryProvider.getIfAvailable(() -> ObservationRegistry.NOOP);
        this.retryTemplate = retryTemplate;
    }

    public Map<String, ChatClient> createChatClients(AiModelProperties properties) {
        Map<String, ChatClient> chatClients = new HashMap<>();
        if (properties.getModels() == null || properties.getModels().isEmpty()) {
            return chatClients;
        }

        for (Map.Entry<String, AiModelProperties.AiModel> entry : properties.getModels().entrySet()) {
            chatClients.put(entry.getKey(), createChatClient(entry.getKey(), entry.getValue()));
        }
        return chatClients;
    }

    public Map<String, ChatClient> createChatClients(List<ModelOptions> modelOptionsList) {
        Map<String, ChatClient> chatClients = new HashMap<>();
        if (modelOptionsList == null || modelOptionsList.isEmpty()) {
            return chatClients;
        }

        for (ModelOptions modelOptions : modelOptionsList) {
            AiModelProperties.AiModel aiModel = toAiModel(modelOptions);
            chatClients.put(modelOptions.getModelName(), createChatClient(modelOptions.getModelName(), aiModel));
        }
        return chatClients;
    }

    public AiModelProperties.AiModel toAiModel(ModelOptions modelOptions) {
        AiModelProperties.AiProviderType providerType = resolveProvider(modelOptions.getProvider());
        AiModelProperties.AiModel aiModel = new AiModelProperties.AiModel();
        aiModel.setProvider(providerType);
        aiModel.setBaseUrl(modelOptions.getBaseUrl());
        aiModel.setApiKey(modelOptions.getApiKey());
        aiModel.setOptions(modelOptions.getOptions());
        return aiModel;
    }

    public ChatClient createChatClient(String modelName, AiModelProperties.AiModel aiModel) {
        ChatModel chatModel = switch (aiModel.getProvider()) {
            case OPENAI, ZHIPUAI, MOONSHOT -> buildOpenAiChatModel(aiModel);
            case DEEPSEEK -> {
                DeepSeekApi deepSeekApi = DeepSeekApi.builder()
                        .apiKey(aiModel.getApiKey())
                        .baseUrl(aiModel.getBaseUrl())
                        .build();
                yield new DeepSeekChatModel(
                        deepSeekApi,
                        optionsResolver.toDeepSeekChatOptions(aiModel.getOptions()),
                        ToolCallingManager.builder().build(),
                        retryTemplate,
                        observationRegistry
                );
            }
            case ANTHROPIC -> {
                AnthropicChatOptions chatOptions = optionsResolver.toAnthropicChatOptions(aiModel.getOptions())
                        .mutate()
                        .apiKey(aiModel.getApiKey())
                        .baseUrl(aiModel.getBaseUrl())
                        .build();
                yield AnthropicChatModel.builder()
                        .options(chatOptions)
                        .observationRegistry(observationRegistry)
                        .build();
            }
            case MINIMAX -> {
                MiniMaxApi miniMaxApi = new MiniMaxApi(
                        aiModel.getBaseUrl(),
                        aiModel.getApiKey()
                );
                yield new MiniMaxChatModel(
                        miniMaxApi,
                        optionsResolver.toMiniMaxChatOptions(aiModel.getOptions()),
                        ToolCallingManager.builder().build(),
                        retryTemplate,
                        observationRegistry,
                        (options, response) -> false
                );
            }
            default -> OllamaChatModel.builder()
                    .defaultOptions(optionsResolver.toOllamaChatOptions(aiModel.getOptions()))
                    .ollamaApi(OllamaApi.builder()
                            .baseUrl(aiModel.getBaseUrl())
                            .build())
                    .observationRegistry(observationRegistry)
                    .build();
        };

        return ChatClient.builder(chatModel).build();
    }

    public EmbeddingModel createEmbeddingModel(String modelName, AiModelProperties.AiModel aiModel) {
        return switch (aiModel.getProvider()) {
            case OPENAI, ZHIPUAI, MOONSHOT, DEEPSEEK, ANTHROPIC, MINIMAX ->
                    buildOpenAiEmbeddingModel(aiModel);
            case OLLAMA -> OllamaEmbeddingModel.builder()
                    .ollamaApi(OllamaApi.builder()
                            .baseUrl(aiModel.getBaseUrl())
                            .build())
                    .defaultOptions(getOllamaEmbeddingOptions(aiModel.getOptions()))
                    .observationRegistry(observationRegistry)
                    .build();
        };
    }

    private OpenAiChatModel buildOpenAiChatModel(AiModelProperties.AiModel aiModel) {
        OpenAiChatOptions chatOptions = optionsResolver.toOpenAiChatOptions(aiModel.getOptions());
        String modelName = resolveConfiguredModel(aiModel);
        Duration timeout = chatOptions.getTimeout();
        int maxRetries = chatOptions.getMaxRetries() > 0 ? chatOptions.getMaxRetries() : DEFAULT_MAX_RETRIES;

        OpenAIClient syncClient = OpenAiSetup.setupSyncClient(
                aiModel.getBaseUrl(),
                aiModel.getApiKey(),
                null,
                null,
                null,
                null,
                false,
                false,
                modelName,
                timeout,
                maxRetries,
                chatOptions.getProxy(),
                chatOptions.getCustomHeaders()
        );
        OpenAIClientAsync asyncClient = OpenAiSetup.setupAsyncClient(
                aiModel.getBaseUrl(),
                aiModel.getApiKey(),
                null,
                null,
                null,
                null,
                false,
                false,
                modelName,
                timeout,
                maxRetries,
                chatOptions.getProxy(),
                chatOptions.getCustomHeaders()
        );

        return OpenAiChatModel.builder()
                .openAiClient(syncClient)
                .openAiClientAsync(asyncClient)
                .options(chatOptions)
                .observationRegistry(observationRegistry)
                .build();
    }

    private OpenAiEmbeddingModel buildOpenAiEmbeddingModel(AiModelProperties.AiModel aiModel) {
        String modelName = resolveConfiguredModel(aiModel);
        OpenAIClient client = OpenAiSetup.setupSyncClient(
                aiModel.getBaseUrl(),
                aiModel.getApiKey(),
                null,
                null,
                null,
                null,
                false,
                false,
                modelName,
                DEFAULT_TIMEOUT,
                DEFAULT_MAX_RETRIES,
                null,
                null
        );

        OpenAiEmbeddingOptions.Builder embeddingOptionsBuilder = OpenAiEmbeddingOptions.builder();
        if (modelName != null) {
            embeddingOptionsBuilder.model(modelName);
        }
        return new OpenAiEmbeddingModel(
                client,
                MetadataMode.EMBED,
                embeddingOptionsBuilder.build(),
                observationRegistry
        );
    }

    private String resolveConfiguredModel(AiModelProperties.AiModel aiModel) {
        if (aiModel.getOptions() == null || aiModel.getOptions().getModel() == null) {
            return null;
        }
        return aiModel.getOptions().getModel();
    }

    private AiModelProperties.AiProviderType resolveProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return AiModelProperties.AiProviderType.OPENAI;
        }
        try {
            return AiModelProperties.AiProviderType.valueOf(provider.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("未知provider: {}，已自动降级为OPENAI兼容协议", provider);
            return AiModelProperties.AiProviderType.OPENAI;
        }
    }

    private OllamaEmbeddingOptions getOllamaEmbeddingOptions(AiModelProperties.AiModelOptions options) {
        if (options == null) {
            return OllamaEmbeddingOptions.builder().build();
        }
        var builder = OllamaEmbeddingOptions.builder();
        if (options.getModel() != null) {
            builder.model(options.getModel());
        }
        return builder.build();
    }
}
