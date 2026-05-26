package com.richie.component.ai.service.impl;

import com.richie.component.ai.config.AiChatClientFactory;
import com.richie.component.ai.config.AiModelProperties;
import com.richie.component.ai.model.AiModelInfo;
import com.richie.component.ai.model.AiRequest;
import com.richie.component.ai.model.AiResponse;
import com.richie.component.ai.model.ModelOptions;
import com.richie.component.ai.service.AiModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI模型服务实现类
 * 提供统一的AI模型调用能力，支持多模型动态切换
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelServiceImpl implements AiModelService {

    @Qualifier("aiChatClients")
    private final Map<String, ChatClient> chatClients;
    private final AiModelProperties aiModelProperties;
    private final AiChatClientFactory aiChatClientFactory;

    /**
     * 默认模型名称
     */
    private String defaultModel;

    /**
     * 模型信息缓存
     */
    private final Map<String, AiModelInfo> modelInfoCache = new ConcurrentHashMap<>();
    private final Map<String, AiModelProperties.AiModel> runtimeModels = new ConcurrentHashMap<>();

    @Override
    public AiResponse call(AiRequest request) {
        AiResponse initializationError = checkInitialized();
        if (initializationError != null) {
            return initializationError;
        }
        try {
            String modelName = getModelName(request);
            ChatClient chatClient = getChatClient(modelName);

            if (chatClient == null) {
                return AiResponse.failure("模型不可用: %s".formatted(modelName), "MODEL_UNAVAILABLE");
            }

            long startTime = System.currentTimeMillis();

            // 构建prompt
            var promptBuilder = chatClient.prompt();
            List<Message> messages = buildMessages(request);
            promptBuilder.messages(messages);

            // 调用AI模型
            var response = promptBuilder.call();

            var chatResponse = response.chatResponse();

            if (chatResponse == null || chatResponse.getResult() == null) {
                return AiResponse.failure("AI模型响应内容为空", "EMPTY_RESPONSE");
            }

            long duration = System.currentTimeMillis() - startTime;

            // 构建响应
            AiResponse aiResponse = AiResponse.success(
                    response.content(),
                    modelName,
                    getProviderName(modelName)
            );

            // 设置使用情况
            if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                var usage = chatResponse.getMetadata().getUsage();
                aiResponse.setUsage(new AiResponse.Usage()
                        .setPromptTokens(usage.getPromptTokens())
                        .setCompletionTokens(usage.getCompletionTokens())
                        .setTotalTokens(usage.getTotalTokens()));
            }

            aiResponse.setDuration(duration);
            aiResponse.setRawResponse(aiResponse.getMetadata());

            log.info("AI模型调用成功 - 模型: {}, 耗时: {}ms", modelName, duration);

            return aiResponse;

        } catch (Exception e) {
            log.error("AI模型调用失败", e);
            return AiResponse.failure("AI模型调用失败: %s".formatted(e.getMessage()), "CALL_FAILED");
        }
    }

    @Override
    public CompletableFuture<AiResponse> callAsync(AiRequest request) {
        return CompletableFuture.supplyAsync(() -> call(request));
    }

    @Override
    public AiResponse callWithModel(String modelName, AiRequest request) {
        AiRequest requestWithModel = request.setModelName(modelName);
        return call(requestWithModel);
    }

    @Override
    public List<AiModelInfo> getAvailableModels() {
        List<AiModelInfo> models = new ArrayList<>();

        for (Map.Entry<String, AiModelProperties.AiModel> entry : getCurrentModels().entrySet()) {
            String modelName = entry.getKey();
            AiModelProperties.AiModel aiModel = entry.getValue();

            AiModelInfo modelInfo = AiModelInfo.of(modelName, aiModel.getProvider().name())
                    .setDescription(getModelDescription(aiModel.getProvider()))
                    .setDefaultModel(modelName.equals(getDefaultModel()))
                    .setCapabilities(getModelCapabilities(aiModel.getProvider()));

            // 检查模型是否可用
            try {
                ChatClient chatClient = chatClients.get(modelName);
                if (chatClient != null) {
                    modelInfo.setAvailable(true);
                } else {
                    modelInfo.setAvailable(false)
                            .setErrorMessage("ChatClient未找到");
                }
            } catch (Exception e) {
                modelInfo.setAvailable(false)
                        .setErrorMessage("模型检查失败: %s".formatted(e.getMessage()));
            }

            models.add(modelInfo);
            modelInfoCache.put(modelName, modelInfo);
        }

        return models;
    }

    @Override
    public AiModelInfo getModelInfo(String modelName) {
        return modelInfoCache.computeIfAbsent(modelName, name -> {
            AiModelProperties.AiModel aiModel = getCurrentModels().get(name);
            if (aiModel == null) {
                return AiModelInfo.unavailable(name, "UNKNOWN", "模型配置不存在");
            }

            return AiModelInfo.of(name, aiModel.getProvider().name())
                    .setDescription(getModelDescription(aiModel.getProvider()))
                    .setDefaultModel(name.equals(getDefaultModel()))
                    .setCapabilities(getModelCapabilities(aiModel.getProvider()));
        });
    }

    @Override
    public boolean isModelAvailable(String modelName) {
        AiModelInfo modelInfo = getModelInfo(modelName);
        return modelInfo.isAvailable();
    }

    @Override
    public String getDefaultModel() {
        if (defaultModel == null) {
            // 如果没有设置默认模型，使用第一个可用的模型
            Map<String, AiModelProperties.AiModel> models = getCurrentModels();
            if (!models.isEmpty()) {
                defaultModel = models.keySet().iterator().next();
            }
        }
        return defaultModel;
    }

    @Override
    public void setDefaultModel(String modelName) {
        if (!isInitialized()) {
            throw new IllegalStateException("AI模型尚未初始化，无法设置默认模型");
        }
        if (getCurrentModels().containsKey(modelName)) {
            this.defaultModel = modelName;
            log.info("设置默认AI模型: {}", modelName);
        } else {
            throw new IllegalArgumentException("模型不存在: %s".formatted(modelName));
        }
    }

    @Override
    public synchronized void initializeModels(List<ModelOptions> modelOptionsList) {
        if (modelOptionsList == null || modelOptionsList.isEmpty()) {
            log.warn("动态初始化模型失败：modelOptionsList为空");
            return;
        }

        Map<String, ChatClient> dynamicClients = aiChatClientFactory.createChatClients(modelOptionsList);
        if (dynamicClients.isEmpty()) {
            log.warn("动态初始化模型失败：未生成任何ChatClient");
            return;
        }

        for (ModelOptions modelOptions : modelOptionsList) {
            if (modelOptions.getModelName() == null || modelOptions.getModelName().isBlank()) {
                continue;
            }
            runtimeModels.put(modelOptions.getModelName(), aiChatClientFactory.toAiModel(modelOptions));
        }

        chatClients.putAll(dynamicClients);
        modelInfoCache.clear();

        if (defaultModel == null || !chatClients.containsKey(defaultModel)) {
            defaultModel = chatClients.keySet().iterator().next();
        }

        log.info("动态初始化AI模型完成，新增/覆盖 {} 个模型，当前总模型数 {}", dynamicClients.size(), chatClients.size());
    }

    /**
     * 获取模型名称
     */
    private String getModelName(AiRequest request) {
        return request.getModelName() != null ? request.getModelName() : getDefaultModel();
    }

    /**
     * 获取ChatClient
     */
    private ChatClient getChatClient(String modelName) {
        return chatClients.get(modelName);
    }

    /**
     * 构建消息列表
     */
    private List<Message> buildMessages(AiRequest request) {
        List<Message> messages = new ArrayList<>();

        for (AiRequest.Message msg : request.getMessages()) {
            switch (msg.getRole().toLowerCase()) {
                case "system" -> messages.add(new SystemMessage(msg.getContent()));
                case "user" -> messages.add(new UserMessage(msg.getContent()));
                case "assistant" ->
                        messages.add(new org.springframework.ai.chat.messages.AssistantMessage(msg.getContent()));
                default -> log.warn("未知的消息角色: {}", msg.getRole());
            }
        }

        return messages;
    }

    /**
     * 获取提供商名称
     */
    private String getProviderName(String modelName) {
        AiModelProperties.AiModel aiModel = getCurrentModels().get(modelName);
        return aiModel != null ? aiModel.getProvider().name() : "UNKNOWN";
    }

    private Map<String, AiModelProperties.AiModel> getCurrentModels() {
        Map<String, AiModelProperties.AiModel> allModels = new ConcurrentHashMap<>();
        if (aiModelProperties.isConfigInitializationEnabled() && aiModelProperties.getModels() != null) {
            allModels.putAll(aiModelProperties.getModels());
        }
        allModels.putAll(runtimeModels);
        return allModels;
    }

    private boolean isInitialized() {
        return !chatClients.isEmpty();
    }

    private AiResponse checkInitialized() {
        if (isInitialized()) {
            return null;
        }
        return AiResponse.failure("AI模型尚未初始化，无法执行调用，请先通过配置文件或 initializeModels 完成初始化", "MODEL_NOT_INITIALIZED");
    }

    /**
     * 获取模型描述
     */
    private String getModelDescription(AiModelProperties.AiProviderType provider) {
        return switch (provider) {
            case OPENAI -> "OpenAI GPT系列模型";
            case DEEPSEEK -> "DeepSeek大语言模型";
            case ZHIPUAI -> "智谱AI大语言模型";
            case ANTHROPIC -> "Anthropic Claude模型";
            case MINIMAX -> "MiniMax大语言模型";
            case MOONSHOT -> "Moonshot大语言模型";
            default -> "Ollama本地模型";
        };
    }

    /**
     * 获取模型能力
     */
    private AiModelInfo.ModelCapabilities getModelCapabilities(AiModelProperties.AiProviderType provider) {
        AiModelInfo.ModelCapabilities capabilities = new AiModelInfo.ModelCapabilities();

        switch (provider) {
            case OPENAI, DEEPSEEK, MINIMAX, MOONSHOT -> capabilities.setSupportsTemperature(true)
                    .setSupportsTopP(true)
                    .setSupportsFrequencyPenalty(true)
                    .setSupportsPresencePenalty(true)
                    .setSupportsStop(true)
                    .setSupportsLogprobs(true);
            case ANTHROPIC -> capabilities.setSupportsTemperature(true)
                    .setSupportsTopP(true)
                    .setSupportsTopK(true)
                    .setSupportsThinking(true);
            default -> capabilities.setSupportsTemperature(true)
                    .setSupportsTopP(true)
                    .setSupportsStop(true);
        }

        return capabilities;
    }
}
