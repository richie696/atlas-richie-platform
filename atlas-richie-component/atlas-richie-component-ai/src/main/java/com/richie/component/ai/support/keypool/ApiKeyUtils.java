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
package com.richie.component.ai.support.keypool;

import com.richie.component.ai.config.chat.AiChatModel;

import com.richie.component.ai.config.multimodal.audio.AbstractAudioModelConfig;
import com.richie.component.ai.config.multimodal.image.ImageEmbeddingModelConfig;
import com.richie.component.ai.config.multimodal.image.ImageModelConfig;
import com.richie.component.ai.config.multimodal.rerank.RerankModelConfig;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 统一的 API Key 解析工具 — 各 config 类的 {@code apiKey: String} (旧) / {@code apiKeys: Set<String>} (新)
 * 兼容入口。
 *
 * <p>决策 (R-N.5):
 * <ol>
 *   <li>YAML 主推 {@code api-keys: Set<String>}</li>
 *   <li>单数 {@code api-key: sk-xxx} 仍支持(向后兼容) — 自动包装成 size=1 的 Set</li>
 *   <li>两者都空 → 返回空 Set(由调用方决定 fail-fast)</li>
 * </ol>
 *
 * @author richie696
 */
public final class ApiKeyUtils {

    private ApiKeyUtils() {
    }

    /**
     * 从 {@code AiChatModel} 解析有效 key Set。
     * <p>优先级: {@code apiKeys} (Set) > {@code apiKey} (String) > 空。
     */
    public static Set<String> resolveKeys(AiChatModel cfg) {
        if (cfg == null) {
            return Collections.emptySet();
        }
        Set<String> keys = cfg.getApiKeys();
        if (keys != null && !keys.isEmpty()) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(keys));
        }
        if (cfg.getApiKey() != null && !cfg.getApiKey().isBlank()) {
            return Collections.singleton(cfg.getApiKey());
        }
        return Collections.emptySet();
    }

    /** Rerank 配置专用。 */
    public static Set<String> resolveKeys(RerankModelConfig cfg) {
        if (cfg == null) {
            return Collections.emptySet();
        }
        Set<String> keys = cfg.getApiKeys();
        if (keys != null && !keys.isEmpty()) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(keys));
        }
        if (cfg.getApiKey() != null && !cfg.getApiKey().isBlank()) {
            return Collections.singleton(cfg.getApiKey());
        }
        return Collections.emptySet();
    }

    /** Image 配置专用。 */
    public static Set<String> resolveKeys(ImageModelConfig cfg) {
        if (cfg == null) {
            return Collections.emptySet();
        }
        Set<String> keys = cfg.getApiKeys();
        if (keys != null && !keys.isEmpty()) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(keys));
        }
        if (cfg.getApiKey() != null && !cfg.getApiKey().isBlank()) {
            return Collections.singleton(cfg.getApiKey());
        }
        return Collections.emptySet();
    }

    /** Image Embedding (CLIP-equivalent) 配置专用。 */
    public static Set<String> resolveKeys(ImageEmbeddingModelConfig cfg) {
        if (cfg == null) {
            return Collections.emptySet();
        }
        Set<String> keys = cfg.getApiKeys();
        if (keys != null && !keys.isEmpty()) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(keys));
        }
        if (cfg.getApiKey() != null && !cfg.getApiKey().isBlank()) {
            return Collections.singleton(cfg.getApiKey());
        }
        return Collections.emptySet();
    }

    /**
     * 通用 — 适用于 TtsModelConfig / SttModelConfig / VoiceChatModelConfig(共享 AbstractAudioModelConfig 字段)。
     */
    public static Set<String> resolveKeys(AbstractAudioModelConfig cfg) {
        if (cfg == null) {
            return Collections.emptySet();
        }
        Set<String> keys = cfg.getApiKeys();
        if (keys != null && !keys.isEmpty()) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(keys));
        }
        if (cfg.getApiKey() != null && !cfg.getApiKey().isBlank()) {
            return Collections.singleton(cfg.getApiKey());
        }
        return Collections.emptySet();
    }
}