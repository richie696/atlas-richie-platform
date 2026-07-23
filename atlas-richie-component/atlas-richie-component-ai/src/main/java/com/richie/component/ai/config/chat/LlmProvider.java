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

import com.richie.component.ai.config.chat.AiChatModel;
import com.richie.component.ai.config.chat.LlmProvider;

/**
 * 大语言模型（LLM）提供商枚举。
 *
 * <p>用于 {@code AiChatModel#getProvider()} — 标识当前 Chat 模型条目由哪个厂商实现提供。
 *
 * <h2>说明</h2>
 * <ul>
 *   <li>历史上叫 {@code AiProviderType},语义过于宽泛(当时还没有多模态),现已重命名为 {@code LlmProvider} 以明确 LLM 域。</li>
 *   <li>多模态模型(Rerank / Image / TTS / STT / VoiceChat)各自有独立枚举 {@code RerankProvider} / {@code ImageProvider} / {@code TtsProvider} / {@code SttProvider} / {@code VoiceChatProvider},因为不同能力的厂商集合不同。</li>
 *   <li>枚举值定义沿用 Spring AI 生态(大写,字符串等同 {@link #name()})。</li>
 * </ul>
 */
public enum LlmProvider {
    OPENAI,
    DEEPSEEK,
    ZHIPUAI,
    ANTHROPIC,
    OLLAMA,
    MINIMAX,
    MOONSHOT
}