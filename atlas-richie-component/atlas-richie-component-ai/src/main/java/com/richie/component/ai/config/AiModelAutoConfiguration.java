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

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Map.Entry;
import java.util.Map;

/**
 * AI模型自动配置类
 * 负责创建和管理各种AI模型的ChatClient实例
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-20 10:39:15
 */
@Slf4j
@Configuration
@ComponentScan("com.richie.component.ai")
@EnableConfigurationProperties({AiModelProperties.class})
public class AiModelAutoConfiguration {

    /**
     * 构造器
     */
    public AiModelAutoConfiguration() {
    }

    /**
     * 创建AI模型ChatClient映射
     * 根据配置创建各种AI模型的ChatClient实例
     *
     * @param properties AI模型配置属性
     * @return 模型名称到ChatClient的映射
     */
    @Bean("aiChatClients")
    public Map<String, ChatClient> aiChatClients(AiModelProperties properties, AiChatClientFactory aiChatClientFactory) {
        if (!properties.isConfigInitializationEnabled()) {
            log.info("AI组件已关闭配置文件初始化，等待运行时动态初始化模型");
            return Map.of();
        }
        Map<String, ChatClient> chatClients = aiChatClientFactory.createChatClients(properties);
        if (chatClients.isEmpty()) {
            log.warn("未配置任何AI模型");
        }
        return chatClients;
    }

    /**
     * 创建默认 EmbeddingModel（跟随默认模型策略：取配置中的首个模型）。
     *
     * <p>{@code @Primary} 仲裁：Spring AI starter（OpenAI / Ollama）会同时注册各自的
     * {@code EmbeddingModel} bean，业务侧依赖本中台组件时应由本 bean 胜出。
     * {@code @ConditionalOnMissingBean(EmbeddingModel.class)} 兜底：业务已自定义 EmbeddingModel 时不再覆盖。
     */
    @Bean("aiEmbeddingModel")
    @Primary
    @ConditionalOnMissingBean(EmbeddingModel.class)
    public EmbeddingModel aiEmbeddingModel(AiModelProperties properties, AiChatClientFactory aiChatClientFactory) {
        if (!properties.isConfigInitializationEnabled()) {
            log.info("AI组件未启用配置初始化，跳过默认EmbeddingModel自动创建，可在业务侧手工声明EmbeddingModel Bean");
            return null;
        }
        if (properties.getModels() == null || properties.getModels().isEmpty()) {
            log.warn("AI组件未配置任何模型，跳过默认EmbeddingModel自动创建，可在业务侧手工声明EmbeddingModel Bean");
            return null;
        }
        Entry<String, AiModelProperties.AiModel> defaultModelEntry = properties.getModels().entrySet().iterator().next();
        String defaultModelName = defaultModelEntry.getKey();
        log.info("初始化默认EmbeddingModel，跟随默认模型: {}", defaultModelName);
        return aiChatClientFactory.createEmbeddingModel(defaultModelName, defaultModelEntry.getValue());
    }
}
