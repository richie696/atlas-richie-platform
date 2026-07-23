/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.ai.config;

import com.richie.component.ai.config.chat.AiChatModel;
import com.richie.component.ai.config.chat.AiChatModelOptions;
import com.richie.component.ai.config.chat.LlmProvider;
import com.richie.component.ai.model.ModelOptions;
import com.richie.component.ai.support.AiChatOptionsResolver;
import com.richie.component.ai.support.keypool.ApiKeyPool;
import com.richie.component.ai.support.keypool.ApiKeyPoolManager;
import com.richie.component.ai.support.keypool.ApiKeyUtils;
import com.richie.component.ai.support.keypool.ApiKeyValidator;
import com.richie.component.ai.support.keypool.DefaultApiKeyValidator;
import com.richie.component.ai.support.keypool.PooledChatModel;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
    private final MeterRegistry meterRegistry;
    private final RetryTemplate retryTemplate;
    private final ApiKeyPoolManager apiKeyPoolManager;
    private final ApiKeyValidator apiKeyValidator;
    private final int apiKeyRetryRounds;

    public AiChatClientFactory(AiChatOptionsResolver optionsResolver,
                               ObjectProvider<ObservationRegistry> observationRegistryProvider,
                               ObjectProvider<MeterRegistry> meterRegistryProvider,
                               RetryTemplate retryTemplate,
                               ApiKeyPoolManager apiKeyPoolManager) {
        this.optionsResolver = optionsResolver;
        this.observationRegistry = observationRegistryProvider.getIfAvailable(() -> ObservationRegistry.NOOP);
        this.meterRegistry = meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new);
        this.retryTemplate = retryTemplate;
        this.apiKeyPoolManager = apiKeyPoolManager;
        this.apiKeyValidator = new DefaultApiKeyValidator();
        this.apiKeyRetryRounds = apiKeyPoolManager != null
                ? 2
                : 1;
    }

    public Map<String, ChatClient> createChatClients(AiModelProperties properties) {
        Map<String, ChatClient> chatClients = new HashMap<>();
        if (properties.getChat() == null || properties.getChat().isEmpty()) {
            return chatClients;
        }

        for (Map.Entry<String, AiChatModel> entry : properties.getChat().entrySet()) {
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
            AiChatModel aiModel = toAiModel(modelOptions);
            chatClients.put(modelOptions.getModelName(), createChatClient(modelOptions.getModelName(), aiModel));
        }
        return chatClients;
    }

    public AiChatModel toAiModel(ModelOptions modelOptions) {
        LlmProvider providerType = resolveProvider(modelOptions.getProvider());
        AiChatModel aiModel = new AiChatModel();
        aiModel.setProvider(providerType);
        aiModel.setBaseUrl(modelOptions.getBaseUrl());
        aiModel.setApiKey(modelOptions.getApiKey());
        aiModel.setOptions(modelOptions.getOptions());
        return aiModel;
    }

    public ChatClient createChatClient(String modelName, AiChatModel aiModel) {
        java.util.Set<String> keys = ApiKeyUtils.resolveKeys(aiModel);
        if (keys.isEmpty()) {
            throw new IllegalStateException(
                    "AiChatModel[" + modelName + "] 缺少 API Key — 请配置 api-keys 或 api-key");
        }

        // OLLAMA 等本地模型不需要 key — 走 NoOp pool (单 client)
        if (aiModel.getProvider() == LlmProvider.OLLAMA || keys.size() == 1) {
            ChatModel chatModel = buildChatModelForKey(aiModel, keys.iterator().next());
            return ChatClient.builder(chatModel).build();
        }

        // 多 key 场景:为每个 key 预建一个 ChatModel,用 PooledChatModel 装饰
        ApiKeyPool pool = apiKeyPoolManager.getPool(modelName, keys);
        java.util.List<ChatModel> perKeyModels = new java.util.ArrayList<>(keys.size());
        for (String keyValue : keys) {
            perKeyModels.add(buildChatModelForKey(aiModel, keyValue));
        }
        PooledChatModel pooled = new PooledChatModel(modelName, perKeyModels, pool, apiKeyValidator, apiKeyRetryRounds);
        log.info("AiChatModel[{}] 已启用 KeyPool: totalKeys={}, retryRounds={}",
                modelName, keys.size(), apiKeyRetryRounds);
        return ChatClient.builder(pooled).build();
    }

    /**
     * 为单个 key 构建 ChatModel(per-key 预建,N 个 key = N 个实例)。
     */
    private ChatModel buildChatModelForKey(AiChatModel aiModel, String apiKeyValue) {
        AiChatModel single = cloneWithApiKey(aiModel, apiKeyValue);
        return switch (single.getProvider()) {
            case OPENAI, ZHIPUAI, MOONSHOT, MINIMAX -> buildOpenAiChatModel(single);
            case DEEPSEEK -> {
                DeepSeekApi deepSeekApi = DeepSeekApi.builder()
                        .apiKey(apiKeyValue)
                        .baseUrl(single.getBaseUrl())
                        .build();
                yield new DeepSeekChatModel(
                        deepSeekApi,
                        optionsResolver.toDeepSeekChatOptions(single.getOptions()),
                        ToolCallingManager.builder().build(),
                        retryTemplate,
                        observationRegistry
                );
            }
            case ANTHROPIC -> {
                AnthropicChatOptions chatOptions = optionsResolver.toAnthropicChatOptions(single.getOptions())
                        .mutate()
                        .apiKey(apiKeyValue)
                        .baseUrl(single.getBaseUrl())
                        .build();
                yield AnthropicChatModel.builder()
                        .options(chatOptions)
                        .observationRegistry(observationRegistry)
                        .build();
            }
            default -> OllamaChatModel.builder()
                    .options(optionsResolver.toOllamaChatOptions(single.getOptions()))
                    .ollamaApi(OllamaApi.builder()
                            .baseUrl(single.getBaseUrl())
                            .build())
                    .observationRegistry(observationRegistry)
                    .build();
        };
    }

    /**
     * 浅拷贝 AiChatModel 并把 apiKey 覆盖成指定值(其他字段共享引用,适合 per-key 实例化)。
     */
    private static AiChatModel cloneWithApiKey(AiChatModel src, String apiKey) {
        AiChatModel copy = new AiChatModel();
        copy.setProvider(src.getProvider());
        copy.setBaseUrl(src.getBaseUrl());
        copy.setApiKey(apiKey);
        copy.setOptions(src.getOptions());
        return copy;
    }

    public EmbeddingModel createEmbeddingModel(String modelName, AiChatModel aiModel) {
        return switch (aiModel.getProvider()) {
            case OPENAI, ZHIPUAI, MOONSHOT, DEEPSEEK, ANTHROPIC, MINIMAX ->
                    buildOpenAiEmbeddingModel(aiModel);
            case OLLAMA -> OllamaEmbeddingModel.builder()
                    .ollamaApi(OllamaApi.builder()
                            .baseUrl(aiModel.getBaseUrl())
                            .build())
                    .options(getOllamaEmbeddingOptions(aiModel.getOptions()))
                    .observationRegistry(observationRegistry)
                    .build();
        };
    }

    private OpenAiChatModel buildOpenAiChatModel(AiChatModel aiModel) {
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
                chatOptions.getCustomHeaders(),
                observationRegistry,
                meterRegistry,
                List.of()
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
                chatOptions.getCustomHeaders(),
                observationRegistry,
                meterRegistry,
                List.of()
        );

        return OpenAiChatModel.builder()
                .openAiClient(syncClient)
                .openAiClientAsync(asyncClient)
                .options(chatOptions)
                .observationRegistry(observationRegistry)
                .build();
    }

    private OpenAiEmbeddingModel buildOpenAiEmbeddingModel(AiChatModel aiModel) {
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
                null,
                observationRegistry,
                meterRegistry,
                List.of()
        );

        OpenAiEmbeddingOptions.Builder embeddingOptionsBuilder = OpenAiEmbeddingOptions.builder();
        if (modelName != null) {
            embeddingOptionsBuilder.model(modelName);
        }
        return OpenAiEmbeddingModel.builder()
                .openAiClient(client)
                .metadataMode(MetadataMode.EMBED)
                .options(embeddingOptionsBuilder.build())
                .observationRegistry(observationRegistry)
                .build();
    }

    private String resolveConfiguredModel(AiChatModel aiModel) {
        if (aiModel.getOptions() == null || aiModel.getOptions().getModel() == null) {
            return null;
        }
        return aiModel.getOptions().getModel();
    }

    private LlmProvider resolveProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return LlmProvider.OPENAI;
        }
        try {
            return LlmProvider.valueOf(provider.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("未知provider: {}，已自动降级为OPENAI兼容协议", provider);
            return LlmProvider.OPENAI;
        }
    }

    private OllamaEmbeddingOptions getOllamaEmbeddingOptions(AiChatModelOptions options) {
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
