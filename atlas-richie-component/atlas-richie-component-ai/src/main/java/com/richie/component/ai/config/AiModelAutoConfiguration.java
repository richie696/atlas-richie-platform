package com.richie.component.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

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
     */
    @Bean("aiEmbeddingModel")
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
