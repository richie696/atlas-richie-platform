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
package com.richie.component.ai.config.chat;

import java.util.LinkedHashSet;
import java.util.Set;

import lombok.Data;

/**
 * 大语言模型(Chat)条目配置 — 映射 {@code platform.component.ai.models.<key>}。
 *
 * <p>提供方由 {@link #provider} 决定,与 R-N 多模态(Rerank / Image / TTS / STT / VoiceChat)
 * 各自有独立枚举 — 不同能力的厂商集合不统一。
 *
 * @author richie696
 */
@Data
public class AiChatModel {

    /** 提供方(LLM 域),见 {@link LlmProvider}。 */
    private LlmProvider provider;

    /** API Key(Chat 端鉴权)。 */
    private String apiKey;

    /** API Key 池 — Token Plan 多 key 轮询 / 限流后冷却。YAML: {@code api-keys: [sk-1, sk-2]}。 */
    private Set<String> apiKeys = new LinkedHashSet<>();

    /** 厂商端点 URL(为空时由 {@code provider} + Spring AI 默认回落)。 */
    private String baseUrl;

    /** 推理参数。 */
    private AiChatModelOptions options;
}