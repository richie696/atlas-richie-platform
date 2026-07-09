/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.ai.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 流式输出片段
 */
@Data
@Accessors(chain = true)
public class AiStreamChunk {

    /**
     * 增量文本
     */
    private String delta;

    /**
     * 是否为最后一个片段
     */
    private boolean finished;

    /**
     * 实际使用的模型名
     */
    private String modelName;

    /**
     * 提供商
     */
    private String provider;

    /**
     * 流结束时的 token 统计（通常仅在 finished=true 时有值）
     */
    private AiResponse.Usage usage;

    /**
     * 流式调用失败时的错误码
     */
    private String errorCode;

    /**
     * 流式调用失败时的错误信息
     */
    private String errorMessage;

    public static AiStreamChunk delta(String delta, String modelName, String provider) {
        return new AiStreamChunk()
                .setDelta(delta)
                .setFinished(false)
                .setModelName(modelName)
                .setProvider(provider);
    }

    public static AiStreamChunk finished(String modelName, String provider, AiResponse.Usage usage) {
        return new AiStreamChunk()
                .setDelta("")
                .setFinished(true)
                .setModelName(modelName)
                .setProvider(provider)
                .setUsage(usage);
    }

    public static AiStreamChunk error(String errorMessage, String errorCode) {
        return new AiStreamChunk()
                .setFinished(true)
                .setErrorMessage(errorMessage)
                .setErrorCode(errorCode);
    }
}
