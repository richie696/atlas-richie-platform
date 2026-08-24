/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.ai.service.impl;

import cn.richie696.component.ai.config.AiChatClientFactory;
import cn.richie696.component.ai.config.AiModelProperties;
import cn.richie696.component.ai.config.AiStsSignerFactory;
import cn.richie696.component.ai.service.AiMultimodalService;
import cn.richie696.component.ai.service.VoiceStsService;
import cn.richie696.component.ai.support.keypool.ApiKeyPoolManager;
import cn.richie696.component.secret.api.SecretSnapshotChangedEvent;
import cn.richie696.component.secret.bootstrap.refresh.PreparedSecretRefresh;
import cn.richie696.component.secret.bootstrap.refresh.SecretRefreshParticipant;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 预建完整 AI Chat/多模态候选快照，并以单次引用替换发布。
 */
@Component
@ConditionalOnProperty(
        prefix = "platform.component.secret",
        name = "enabled",
        havingValue = "true")
public final class AiSecretRefreshParticipant implements SecretRefreshParticipant {
    private final AiChatServiceImpl chatService;
    private final AiMultimodalServiceImpl multimodalService;
    private final AiChatClientFactory clientFactory;
    private final ApiKeyPoolManager apiKeyPoolManager;
    private final VoiceStsServiceImpl voiceStsService;

    public AiSecretRefreshParticipant(
            AiChatServiceImpl chatService,
            @Qualifier("aiMultimodalService") AiMultimodalService multimodalService,
            AiChatClientFactory clientFactory,
            ApiKeyPoolManager apiKeyPoolManager,
            @Qualifier("aiVoiceStsService") VoiceStsService voiceStsService) {
        this.chatService = chatService;
        if (!(multimodalService instanceof AiMultimodalServiceImpl implementation)) {
            throw new IllegalStateException("Secret refresh requires the standard AI multimodal service");
        }
        this.multimodalService = implementation;
        this.clientFactory = clientFactory;
        this.apiKeyPoolManager = apiKeyPoolManager;
        if (!(voiceStsService instanceof VoiceStsServiceImpl stsImplementation)) {
            throw new IllegalStateException("Secret refresh requires the standard AI STS service");
        }
        this.voiceStsService = stsImplementation;
    }

    @Override
    public PreparedSecretRefresh prepare(
            ConfigurableEnvironment environment,
            SecretSnapshotChangedEvent candidate) {
        AiModelProperties properties = Binder.get(environment)
                .bind("platform.component.ai", AiModelProperties.class)
                .orElseGet(AiModelProperties::new);
        ApiKeyPoolManager.PreparedGeneration<Map<String, ChatClient>> keyPools =
                apiKeyPoolManager.prepareSecretRefresh(
                        properties, () -> clientFactory.createChatClients(properties));
        Map<String, ChatClient> chatClients = keyPools.value();
        if (properties.getChat() != null && chatClients.size() != properties.getChat().size()) {
            keyPools.refresh().rollback();
            throw new IllegalStateException("AI Chat Secret refresh candidate is incomplete");
        }
        try {
            PreparedSecretRefresh chat = chatService.prepareSecretRefresh(properties, chatClients);
            PreparedSecretRefresh multimodal = multimodalService.prepareSecretRefresh(properties);
            PreparedSecretRefresh signers = voiceStsService.prepareSecretRefresh(
                    AiStsSignerFactory.createAll(properties));
            return composite(List.of(keyPools.refresh(), chat, multimodal, signers));
        } catch (RuntimeException failure) {
            keyPools.refresh().rollback();
            throw failure;
        }
    }

    private PreparedSecretRefresh composite(List<PreparedSecretRefresh> changes) {
        return new PreparedSecretRefresh() {
            @Override
            public void commit() {
                changes.forEach(PreparedSecretRefresh::commit);
            }

            @Override
            public void rollback() {
                for (int index = changes.size() - 1; index >= 0; index--) {
                    changes.get(index).rollback();
                }
            }

            @Override
            public void complete() {
                changes.forEach(PreparedSecretRefresh::complete);
            }
        };
    }
}
