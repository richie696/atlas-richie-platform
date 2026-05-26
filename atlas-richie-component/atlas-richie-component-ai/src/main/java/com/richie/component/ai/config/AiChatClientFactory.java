package com.richie.component.ai.config;

import com.richie.component.ai.model.ModelOptions;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.ai.minimax.MiniMaxChatOptions;
import org.springframework.ai.minimax.api.MiniMaxApi;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI ChatClient工厂
 * 统一负责根据配置构建各类模型客户端，支持配置文件与运行时动态初始化
 *
 * @author richie696
 * @version 1.0
 * @since 2026-04-22
 */
@Slf4j
@Component
public class AiChatClientFactory {

    /**
     * 基于配置文件模型配置初始化ChatClient映射
     */
    public Map<String, ChatClient> createChatClients(AiModelProperties properties) {
        Map<String, ChatClient> chatClients = new HashMap<>();
        if (properties.getModels() == null || properties.getModels().isEmpty()) {
            return chatClients;
        }

        for (Map.Entry<String, AiModelProperties.AiModel> entry : properties.getModels().entrySet()) {
            String modelName = entry.getKey();
            AiModelProperties.AiModel aiModel = entry.getValue();
            chatClients.put(modelName, createChatClient(modelName, aiModel));
        }
        return chatClients;
    }

    /**
     * 基于运行时模型配置初始化ChatClient映射
     * 对未知provider自动降级为OPENAI协议
     */
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

    /**
     * 将运行时模型配置转为组件内部配置对象
     */
    public AiModelProperties.AiModel toAiModel(ModelOptions modelOptions) {
        AiModelProperties.AiProviderType providerType = resolveProvider(modelOptions.getProvider());
        AiModelProperties.AiModel aiModel = new AiModelProperties.AiModel();
        aiModel.setProvider(providerType);
        aiModel.setBaseUrl(modelOptions.getBaseUrl());
        aiModel.setApiKey(modelOptions.getApiKey());
        aiModel.setOptions(modelOptions.getOptions());
        return aiModel;
    }

    /**
     * 创建ChatClient实例
     */
    public ChatClient createChatClient(String modelName, AiModelProperties.AiModel aiModel) {
        ChatModel chatModel = switch (aiModel.getProvider()) {
            // ChatGPT
            case OPENAI -> {
                OpenAiApi openAiApi = OpenAiApi.builder()
                        .apiKey(aiModel.getApiKey())
                        .baseUrl(aiModel.getBaseUrl())
                        .build();
                OpenAiChatOptions chatOptions = getOpenAiChatOptions(aiModel.getOptions());
                yield new OpenAiChatModel(
                        openAiApi,
                        chatOptions,
                        ToolCallingManager.builder().build(),
                        RetryUtils.DEFAULT_RETRY_TEMPLATE,
                        ObservationRegistry.NOOP
                );
            }

            // DeepSeek
            case DEEPSEEK -> {
                DeepSeekApi deepSeekApi = DeepSeekApi.builder()
                        .apiKey(aiModel.getApiKey())
                        .baseUrl(aiModel.getBaseUrl())
                        .build();
                DeepSeekChatOptions chatOptions = getDeepSeekChatOptions(aiModel.getOptions());
                yield new DeepSeekChatModel(
                        deepSeekApi,
                        chatOptions,
                        ToolCallingManager.builder().build(),
                        RetryUtils.DEFAULT_RETRY_TEMPLATE,
                        ObservationRegistry.NOOP
                );
            }

            // 智谱AI（使用OpenAI兼容协议）
            case ZHIPUAI -> {
                OpenAiApi zhiPuAiApi = OpenAiApi.builder()
                        .apiKey(aiModel.getApiKey())
                        .baseUrl(aiModel.getBaseUrl())
                        .build();
                OpenAiChatOptions chatOptions = getOpenAiChatOptions(aiModel.getOptions());
                yield new OpenAiChatModel(
                        zhiPuAiApi,
                        chatOptions,
                        ToolCallingManager.builder().build(),
                        RetryUtils.DEFAULT_RETRY_TEMPLATE,
                        ObservationRegistry.NOOP
                );
            }

            // Claude
            case ANTHROPIC -> {
                AnthropicChatOptions chatOptions = getAnthropicChatOptions(aiModel.getOptions())
                        .mutate()
                        .apiKey(aiModel.getApiKey())
                        .baseUrl(aiModel.getBaseUrl())
                        .build();
                yield AnthropicChatModel.builder()
                        .options(chatOptions)
                        .build();
            }

            // MiniMax
            case MINIMAX -> {
                MiniMaxApi miniMaxApi = new MiniMaxApi(
                        aiModel.getBaseUrl(),
                        aiModel.getApiKey()
                );
                MiniMaxChatOptions chatOptions = getMiniMaxChatOptions(aiModel.getOptions());
                yield new MiniMaxChatModel(
                        miniMaxApi,
                        chatOptions,
                        ToolCallingManager.builder().build(),
                        RetryUtils.DEFAULT_RETRY_TEMPLATE
                );
            }

            // Moonshot（使用OpenAI兼容协议）
            case MOONSHOT -> {
                OpenAiApi moonshotApi = OpenAiApi.builder()
                        .apiKey(aiModel.getApiKey())
                        .baseUrl(aiModel.getBaseUrl())
                        .build();
                OpenAiChatOptions chatOptions = getOpenAiChatOptions(aiModel.getOptions());
                yield new OpenAiChatModel(
                        moonshotApi,
                        chatOptions,
                        ToolCallingManager.builder().build(),
                        RetryUtils.DEFAULT_RETRY_TEMPLATE,
                        ObservationRegistry.NOOP
                );
            }

            // Ollama
            default -> OllamaChatModel.builder()
                    .defaultOptions(getOllamaOptions(aiModel.getOptions()))
                    .ollamaApi(OllamaApi.builder()
                            .baseUrl(aiModel.getBaseUrl())
                            .build())
                    .build();
        };

        return ChatClient.builder(chatModel).build();
    }

    /**
     * 根据模型配置创建 EmbeddingModel 实例。
     */
    public EmbeddingModel createEmbeddingModel(String modelName, AiModelProperties.AiModel aiModel) {
        return switch (aiModel.getProvider()) {
            case OPENAI, ZHIPUAI, MOONSHOT -> {
                OpenAiApi openAiApi = OpenAiApi.builder()
                        .apiKey(aiModel.getApiKey())
                        .baseUrl(aiModel.getBaseUrl())
                        .build();
                yield new OpenAiEmbeddingModel(openAiApi);
            }
            case DEEPSEEK -> {
                OpenAiApi openAiApi = OpenAiApi.builder()
                        .apiKey(aiModel.getApiKey())
                        .baseUrl(aiModel.getBaseUrl())
                        .build();
                yield new OpenAiEmbeddingModel(openAiApi);
            }
            case OLLAMA -> OllamaEmbeddingModel.builder()
                    .ollamaApi(OllamaApi.builder()
                            .baseUrl(aiModel.getBaseUrl())
                            .build())
                    .defaultOptions(getOllamaEmbeddingOptions(aiModel.getOptions()))
                    .build();
            // 部分 provider 无官方 EmbeddingModel 实现，先回退到 OpenAI 兼容协议。
            case ANTHROPIC, MINIMAX -> {
                OpenAiApi openAiApi = OpenAiApi.builder()
                        .apiKey(aiModel.getApiKey())
                        .baseUrl(aiModel.getBaseUrl())
                        .build();
                yield new OpenAiEmbeddingModel(openAiApi);
            }
        };
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

    private OpenAiChatOptions getOpenAiChatOptions(AiModelProperties.AiModelOptions options) {
        if (options == null) {
            return OpenAiChatOptions.builder().build();
        }

        var builder = OpenAiChatOptions.builder();

        if (options.getModel() != null) {
            builder.model(options.getModel());
        }
        if (options.getMaxTokens() != null) {
            builder.maxTokens(options.getMaxTokens());
        }
        if (options.getTemperature() != null) {
            builder.temperature(options.getTemperature());
        }
        if (options.getTopP() != null) {
            builder.topP(options.getTopP());
        }
        if (options.getFrequencyPenalty() != null) {
            builder.frequencyPenalty(options.getFrequencyPenalty());
        }
        if (options.getPresencePenalty() != null) {
            builder.presencePenalty(options.getPresencePenalty());
        }
        if (options.getStop() != null && !options.getStop().isEmpty()) {
            builder.stop(options.getStop());
        }
        if (options.getLogprobs() != null) {
            builder.logprobs(options.getLogprobs());
        }
        if (options.getTopLogprobs() != null) {
            builder.topLogprobs(options.getTopLogprobs());
        }

        return builder.build();
    }

    private OllamaChatOptions getOllamaOptions(AiModelProperties.AiModelOptions options) {
        if (options == null) {
            return OllamaChatOptions.builder().build();
        }

        var builder = OllamaChatOptions.builder();

        if (options.getModel() != null) {
            builder.model(options.getModel());
        }
        if (options.getMaxTokens() != null) {
            builder.numPredict(options.getMaxTokens());
        }
        if (options.getTemperature() != null) {
            builder.temperature(options.getTemperature());
        }
        if (options.getTopP() != null) {
            builder.topP(options.getTopP());
        }
        if (options.getStop() != null && !options.getStop().isEmpty()) {
            builder.stop(options.getStop());
        }

        return builder.build();
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

    private DeepSeekChatOptions getDeepSeekChatOptions(AiModelProperties.AiModelOptions options) {
        if (options == null) {
            return DeepSeekChatOptions.builder().build();
        }

        var builder = DeepSeekChatOptions.builder();

        if (options.getModel() != null) {
            builder.model(options.getModel());
        }
        if (options.getMaxTokens() != null) {
            builder.maxTokens(options.getMaxTokens());
        }
        if (options.getTemperature() != null) {
            builder.temperature(options.getTemperature());
        }
        if (options.getTopP() != null) {
            builder.topP(options.getTopP());
        }
        if (options.getFrequencyPenalty() != null) {
            builder.frequencyPenalty(options.getFrequencyPenalty());
        }
        if (options.getPresencePenalty() != null) {
            builder.presencePenalty(options.getPresencePenalty());
        }
        if (options.getStop() != null && !options.getStop().isEmpty()) {
            builder.stop(options.getStop());
        }
        if (options.getLogprobs() != null) {
            builder.logprobs(options.getLogprobs());
        }
        if (options.getTopLogprobs() != null) {
            builder.topLogprobs(options.getTopLogprobs());
        }

        return builder.build();
    }

    private AnthropicChatOptions getAnthropicChatOptions(AiModelProperties.AiModelOptions options) {
        if (options == null) {
            return AnthropicChatOptions.builder().build();
        }

        var builder = AnthropicChatOptions.builder();

        if (options.getModel() != null) {
            builder.model(options.getModel());
        }
        if (options.getMaxTokens() != null) {
            builder.maxTokens(options.getMaxTokens());
        }
        if (options.getTemperature() != null) {
            builder.temperature(options.getTemperature());
        }
        if (options.getTopP() != null) {
            builder.topP(options.getTopP());
        }
        if (options.getTopK() != null) {
            builder.topK(options.getTopK());
        }

        return builder.build();
    }

    private MiniMaxChatOptions getMiniMaxChatOptions(AiModelProperties.AiModelOptions options) {
        if (options == null) {
            return MiniMaxChatOptions.builder().build();
        }

        var builder = MiniMaxChatOptions.builder();

        if (options.getModel() != null) {
            builder.model(options.getModel());
        }
        if (options.getMaxTokens() != null) {
            builder.maxTokens(options.getMaxTokens());
        }
        if (options.getTemperature() != null) {
            builder.temperature(options.getTemperature());
        }
        if (options.getTopP() != null) {
            builder.topP(options.getTopP());
        }
        if (options.getFrequencyPenalty() != null) {
            builder.frequencyPenalty(options.getFrequencyPenalty());
        }
        if (options.getPresencePenalty() != null) {
            builder.presencePenalty(options.getPresencePenalty());
        }
        if (options.getStop() != null && !options.getStop().isEmpty()) {
            builder.stop(options.getStop());
        }

        return builder.build();
    }
}

