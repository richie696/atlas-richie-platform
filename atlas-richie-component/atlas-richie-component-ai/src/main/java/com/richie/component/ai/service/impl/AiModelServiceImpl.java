/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.ai.service.impl;

import com.richie.component.ai.config.AiChatClientFactory;
import com.richie.component.ai.config.AiModelProperties;
import com.richie.component.ai.model.AiHealthResult;
import com.richie.component.ai.model.AiModelInfo;
import com.richie.component.ai.model.AiRequest;
import com.richie.component.ai.model.AiResponse;
import com.richie.component.ai.model.AiStreamChunk;
import com.richie.component.ai.model.ModelOptions;
import com.richie.component.ai.service.AiModelService;
import com.richie.component.ai.support.AiChatOptionsResolver;
import com.richie.component.ai.support.AiModelCircuitBreaker;
import com.richie.component.ai.support.AiModelRouter;
import com.richie.component.ai.support.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI模型服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelServiceImpl implements AiModelService {

    @Qualifier("aiChatClients")
    private final Map<String, ChatClient> chatClients;
    private final AiModelProperties aiModelProperties;
    private final AiChatClientFactory aiChatClientFactory;
    private final AiChatOptionsResolver optionsResolver;
    private final AiModelRouter aiModelRouter;
    private final AiModelCircuitBreaker circuitBreaker;
    private final ToolRegistry toolRegistry;

    private String defaultModel;
    private final Map<String, AiModelInfo> modelInfoCache = new ConcurrentHashMap<>();
    private final Map<String, AiModelProperties.AiModel> runtimeModels = new ConcurrentHashMap<>();

    @Override
    public AiResponse call(AiRequest request) {
        AiResponse initializationError = checkInitialized();
        if (initializationError != null) {
            return initializationError;
        }

        List<String> chain = resolveChain(request);
        if (chain.isEmpty()) {
            return AiResponse.failure("无可用模型", "NO_MODEL_AVAILABLE");
        }

        AiResponse lastFailure = null;
        for (String modelName : chain) {
            if (!circuitBreaker.allow(modelName, aiModelProperties.getResilience())) {
                lastFailure = AiResponse.failure("模型熔断中: %s".formatted(modelName), "CIRCUIT_OPEN");
                continue;
            }
            AiResponse response = callSingleModel(modelName, request);
            if (response.isSuccess()) {
                circuitBreaker.recordSuccess(modelName);
                return response;
            }
            circuitBreaker.recordFailure(modelName, aiModelProperties.getResilience());
            lastFailure = response;
            log.warn("模型 {} 调用失败，errorCode={}，尝试降级", modelName, response.getErrorCode());
        }
        return lastFailure != null
                ? lastFailure
                : AiResponse.failure("所有模型调用失败", "ALL_MODELS_FAILED");
    }

    @Override
    public CompletableFuture<AiResponse> callAsync(AiRequest request) {
        return CompletableFuture.supplyAsync(() -> call(request));
    }

    @Override
    public Flux<AiStreamChunk> stream(AiRequest request) {
        AiResponse initializationError = checkInitialized();
        if (initializationError != null) {
            return Flux.just(AiStreamChunk.error(initializationError.getErrorMessage(), initializationError.getErrorCode()));
        }

        List<String> chain = resolveChain(request);
        if (chain.isEmpty()) {
            return Flux.just(AiStreamChunk.error("无可用模型", "NO_MODEL_AVAILABLE"));
        }

        for (String modelName : chain) {
            if (!circuitBreaker.allow(modelName, aiModelProperties.getResilience())) {
                continue;
            }
            ChatClient chatClient = getChatClient(modelName);
            if (chatClient == null) {
                continue;
            }
            return streamSingleModel(modelName, request, chatClient);
        }
        return Flux.just(AiStreamChunk.error("所有模型不可用或处于熔断状态", "ALL_MODELS_FAILED"));
    }

    @Override
    public AiResponse callWithModel(String modelName, AiRequest request) {
        return call(request.setModelName(modelName));
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

            ChatClient chatClient = chatClients.get(modelName);
            if (chatClient != null) {
                modelInfo.setAvailable(true);
                if (circuitBreaker.isOpen(modelName)) {
                    modelInfo.setAvailable(false).setErrorMessage("模型熔断中");
                }
            } else {
                modelInfo.setAvailable(false).setErrorMessage("ChatClient未找到");
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
        return chatClients.containsKey(modelName) && !circuitBreaker.isOpen(modelName);
    }

    @Override
    public String getDefaultModel() {
        if (defaultModel == null) {
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

    @Override
    public synchronized void removeModel(String modelName) {
        chatClients.remove(modelName);
        runtimeModels.remove(modelName);
        modelInfoCache.remove(modelName);
        if (modelName != null && modelName.equals(defaultModel)) {
            defaultModel = chatClients.isEmpty() ? null : chatClients.keySet().iterator().next();
        }
        log.info("已移除AI模型: {}", modelName);
    }

    @Override
    public AiHealthResult probe(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return AiHealthResult.unhealthy(modelName, "UNKNOWN", false, "模型名称为空");
        }
        if (!chatClients.containsKey(modelName)) {
            return AiHealthResult.unhealthy(modelName, getProviderName(modelName), false, "ChatClient不存在");
        }

        String provider = getProviderName(modelName);
        if (!aiModelProperties.getHealthCheck().isLiveProbe()) {
            return AiHealthResult.healthy(modelName, provider, false, 0);
        }

        AiRequest ping = new AiRequest()
                .setModelName(modelName)
                .setMessages(List.of(new AiRequest.Message().setRole("user").setContent("ping")))
                .setOptions(new AiRequest.ModelOptions()
                        .setMaxTokens(aiModelProperties.getHealthCheck().getProbeMaxTokens()));

        long start = System.currentTimeMillis();
        AiResponse response = callSingleModel(modelName, ping);
        long duration = System.currentTimeMillis() - start;
        if (response.isSuccess()) {
            return AiHealthResult.healthy(modelName, provider, true, duration);
        }
        return AiHealthResult.unhealthy(modelName, provider, true, response.getErrorMessage());
    }

    @Override
    public List<AiHealthResult> probeAll() {
        return chatClients.keySet().stream().map(this::probe).toList();
    }

    private AiResponse callSingleModel(String modelName, AiRequest request) {
        try {
            ChatClient chatClient = getChatClient(modelName);
            if (chatClient == null) {
                return AiResponse.failure("模型不可用: %s".formatted(modelName), "MODEL_UNAVAILABLE");
            }

            long startTime = System.currentTimeMillis();
            var promptBuilder = chatClient.prompt();
            promptBuilder.messages(buildMessages(request));
            applyRuntimeOptions(promptBuilder, modelName, request);
            applyTools(promptBuilder, request);

            var response = promptBuilder.call();
            var chatResponse = response.chatResponse();

            if (chatResponse == null || chatResponse.getResult() == null) {
                return AiResponse.failure("AI模型响应内容为空", "EMPTY_RESPONSE");
            }

            long duration = System.currentTimeMillis() - startTime;
            AiResponse aiResponse = AiResponse.success(
                    response.content(),
                    modelName,
                    getProviderName(modelName)
            );
            aiResponse.setUsage(extractUsage(chatResponse));
            aiResponse.setDuration(duration);

            log.info("AI模型调用成功 - 模型: {}, 耗时: {}ms", modelName, duration);
            return aiResponse;
        } catch (Exception e) {
            log.error("AI模型 {} 调用失败", modelName, e);
            return AiResponse.failure("AI模型调用失败: %s".formatted(e.getMessage()), "CALL_FAILED");
        }
    }

    private Flux<AiStreamChunk> streamSingleModel(String modelName, AiRequest request, ChatClient chatClient) {
        String provider = getProviderName(modelName);
        AtomicReference<ChatResponse> lastResponse = new AtomicReference<>();

        var promptBuilder = chatClient.prompt();
        promptBuilder.messages(buildMessages(request));
        applyRuntimeOptions(promptBuilder, modelName, request);
        applyTools(promptBuilder, request);

        Flux<AiStreamChunk> deltas = promptBuilder.stream()
                .chatResponse()
                .doOnNext(lastResponse::set)
                .flatMap(chatResponse -> {
                    if (chatResponse.getResult() == null) {
                        return Flux.empty();
                    } else {
                        chatResponse.getResult();
                    }
                    String text = chatResponse.getResult().getOutput().getText();
                    if (text == null || text.isEmpty()) {
                        return Flux.empty();
                    }
                    return Flux.just(AiStreamChunk.delta(text, modelName, provider));
                });

        return deltas.concatWith(Mono.fromSupplier(() -> {
                    circuitBreaker.recordSuccess(modelName);
                    return AiStreamChunk.finished(modelName, provider, extractUsage(lastResponse.get()));
                }))
                .onErrorResume(error -> {
                    circuitBreaker.recordFailure(modelName, aiModelProperties.getResilience());
                    log.error("AI流式调用失败 - 模型: {}", modelName, error);
                    return Flux.just(AiStreamChunk.error(
                            "AI流式调用失败: %s".formatted(error.getMessage()),
                            "STREAM_FAILED"));
                });
    }

    private void applyRuntimeOptions(ChatClient.ChatClientRequestSpec promptBuilder,
                                     String modelName,
                                     AiRequest request) {
        if (!optionsResolver.hasRequestLevelOverride(request)) {
            return;
        }
        AiModelProperties.AiModel aiModel = getCurrentModels().get(modelName);
        if (aiModel == null) {
            return;
        }
        AiModelProperties.AiModelOptions merged = optionsResolver.mergeOptions(aiModel.getOptions(), request.getOptions());
        ChatOptions chatOptions = optionsResolver.toChatOptions(aiModel.getProvider(), merged);
        promptBuilder.options(chatOptions.mutate());
    }

    private void applyTools(ChatClient.ChatClientRequestSpec promptBuilder, AiRequest request) {
        List<ToolCallback> tools = toolRegistry.resolve(request.getToolNames());
        if (tools.isEmpty()) {
            return;
        }
        promptBuilder.tools((Object) tools.toArray(new ToolCallback[0]));
        log.debug("已附加 {} 个工具到请求: {}", tools.size(), request.getToolNames());
    }

    private AiResponse.Usage extractUsage(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return null;
        }
        var usage = chatResponse.getMetadata().getUsage();
        if (usage == null) {
            return null;
        }
        return new AiResponse.Usage()
                .setPromptTokens(usage.getPromptTokens())
                .setCompletionTokens(usage.getCompletionTokens())
                .setTotalTokens(usage.getTotalTokens());
    }

    private List<String> resolveChain(AiRequest request) {
        return aiModelRouter.resolveModelChain(request, getDefaultModel(), chatClients, aiModelProperties);
    }

    private ChatClient getChatClient(String modelName) {
        return chatClients.get(modelName);
    }

    private List<Message> buildMessages(AiRequest request) {
        List<Message> messages = new ArrayList<>();
        if (request.getMessages() == null) {
            return messages;
        }
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
        return AiResponse.failure(
                "AI模型尚未初始化，无法执行调用，请先通过配置文件或 initializeModels 完成初始化",
                "MODEL_NOT_INITIALIZED");
    }

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
