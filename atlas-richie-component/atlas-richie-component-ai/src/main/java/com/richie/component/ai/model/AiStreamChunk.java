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
