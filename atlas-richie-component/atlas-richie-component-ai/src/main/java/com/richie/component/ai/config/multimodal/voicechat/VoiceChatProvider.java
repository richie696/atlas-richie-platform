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
package com.richie.component.ai.config.multimodal.voicechat;

/**
 * 实时语音对话(VoiceChat / WS 双工流式)模型提供商枚举。
 *
 * <p>与 TTS/STT 不同:VoiceChat 是 WebSocket 双工流式,厂商各自提供独立的实时通信端点
 * (Zhipu Realtime / DashScope qwen-omni / Doubao openspeech / Hunyuan STT 流式)。
 */
public enum VoiceChatProvider {
    ZHIPU,
    DASHSCOPE,
    DOUBAO_OPENSPEECH,
    HUNYUAN_STT
}