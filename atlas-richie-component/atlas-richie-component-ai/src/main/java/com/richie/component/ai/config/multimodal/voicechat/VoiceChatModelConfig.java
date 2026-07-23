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

import com.richie.component.ai.config.multimodal.audio.AbstractAudioModelConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 实时语音对话(VoiceChat / WS 双工流式)模型配置 — 映射 {@code platform.component.ai.voice-chat.<key>}。
 *
 * <p>R-N §14.4: 业务名 → 配置(vendor / model / sts)。
 * 凭证字段复用 {@link AbstractAudioModelConfig}(apiKey / secretId+secretKey / appCode / resourceId / appId),
 * 不新增冗余字段,符合原则 H(凭证复用)。
 *
 * @author richie696
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class VoiceChatModelConfig extends AbstractAudioModelConfig {

    /** 厂商(枚举)— 见 {@link VoiceChatProvider}。 */
    private VoiceChatProvider vendor;
}