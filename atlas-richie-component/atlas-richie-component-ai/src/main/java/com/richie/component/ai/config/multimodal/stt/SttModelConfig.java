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
package com.richie.component.ai.config.multimodal.stt;

import com.richie.component.ai.config.multimodal.audio.AbstractAudioModelConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 语音识别(STT / Transcription)模型配置 — 映射 {@code platform.component.ai.stt.<key>}。
 *
 * @author richie696
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SttModelConfig extends AbstractAudioModelConfig {

    /** 厂商(枚举)— 见 {@link SttProvider}。 */
    private SttProvider provider;
}