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

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 大语言模型(Chat)条目参数。
 *
 * <p>仅承载 Chat 模型选项(temperature / topP / topK / maxTokens / 推理参数等),
 * 鉴权 + 端点字段见 {@link AiChatModel}。
 *
 * @author richie696
 */
@Data
@Accessors(chain = true)
public class AiChatModelOptions {

    private String model;
    private Integer maxTokens;
    private Double temperature;
    private Double topP;
    private Integer topK;
    private Double frequencyPenalty;
    private Double presencePenalty;
    private java.util.List<String> stop;
    private Boolean logprobs;
    private Integer topLogprobs;
    private Boolean enableThinking;
    private Integer thinkingBudgetTokens;
}