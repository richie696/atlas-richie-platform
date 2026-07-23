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
import com.richie.component.ai.config.health.HealthCheckConfig;
import com.richie.component.ai.config.keypool.KeyPoolProperties;
import com.richie.component.ai.config.multimodal.image.ImageEmbeddingModelConfig;
import com.richie.component.ai.config.multimodal.image.ImageModelConfig;
import com.richie.component.ai.config.multimodal.rerank.RerankModelConfig;
import com.richie.component.ai.config.multimodal.stt.SttModelConfig;
import com.richie.component.ai.config.multimodal.tts.TtsModelConfig;
import com.richie.component.ai.config.multimodal.voicechat.VoiceChatModelConfig;
import com.richie.component.ai.config.resilience.ResilienceConfig;
import com.richie.component.ai.config.routing.RoutingConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 组件配置总入口 — 映射 {@code platform.component.ai}。
 *
 * <p>各能力(Llm / Rerank / Image / Tts / Stt / VoiceChat)的配置子类已拆分到独立子包,
 * vendor 字段按能力类型分别定义为 {@code LlmProvider} / {@code RerankProvider} / 等枚举,
 * 因为不同能力的厂商集合并不统一,实际使用会混搭。
 *
 * <h2>配置节点</h2>
 * <ul>
 *   <li>{@code platform.component.ai.models.<key>}: 大语言模型(Chat)</li>
 *   <li>{@code platform.component.ai.rerank.<key>}: 重排序模型</li>
 *   <li>{@code platform.component.ai.image.<key>}: 文生图模型</li>
 *   <li>{@code platform.component.ai.image-embedding.<key>}: 多模态向量模型(CLIP 等效,1024 维)</li>
 *   <li>{@code platform.component.ai.tts.<key>}: 语音合成模型</li>
 *   <li>{@code platform.component.ai.stt.<key>}: 语音识别模型</li>
 *   <li>{@code platform.component.ai.voice-chat.<key>}: 实时语音对话(WebSocket)模型</li>
 *   <li>{@code platform.component.ai.routing.*}: 路由 / 降级</li>
 *   <li>{@code platform.component.ai.resilience.*}: 熔断</li>
 *   <li>{@code platform.component.ai.health-check.*}: 健康检查</li>
 * </ul>
 *
 * @author richie696
 * @since 2023-09-05
 */
@Data
@ConfigurationProperties(prefix = "platform.component.ai")
public class AiModelProperties {

    public AiModelProperties() {
    }

    /** 启动时按 application.yml 初始化模型开关(true);false 仅允许运行时动态初始化。 */
    private boolean configInitializationEnabled = true;

    /** 模型路由与降级配置 */
    private RoutingConfig routing = new RoutingConfig();

    /** 调用韧性配置(熔断 / 重试等) */
    private ResilienceConfig resilience = new ResilienceConfig();

    /** 健康检查配置 */
    private HealthCheckConfig healthCheck = new HealthCheckConfig();

    /** API Key 池全局配置 — 映射 {@code platform.component.ai.key-pool}。 */
    private KeyPoolProperties keyPool = new KeyPoolProperties();

    /** 重排序模型映射 — 键为业务名。 */
    private Map<String, RerankModelConfig> rerank = new LinkedHashMap<>();

    /** 文生图模型映射。 */
    private Map<String, ImageModelConfig> image = new LinkedHashMap<>();

    /** 多模态向量模型映射(CLIP 等效) — {@code platform.component.ai.image-embedding.<key>}。 */
    private Map<String, ImageEmbeddingModelConfig> imageEmbedding = new LinkedHashMap<>();

    /** 语音合成模型映射。 */
    private Map<String, TtsModelConfig> tts = new LinkedHashMap<>();

    /** 语音识别模型映射。 */
    private Map<String, SttModelConfig> stt = new LinkedHashMap<>();

    /** 实时语音对话(WebSocket 双工流式)模型映射 — R-N §14.4。 */
    private Map<String, VoiceChatModelConfig> voiceChat = new LinkedHashMap<>();

    /** 大语言模型映射 — 键为业务名。 */
    private Map<String, AiChatModel> chat;
}