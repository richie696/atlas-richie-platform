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
package com.richie.component.ai.api;

import lombok.Data;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * AI 模型调用响应共享基类。
 * <p>
 * R-N §3.0 设计: 各能力(Rerank / Image / TTS / STT / Video / Music / VoiceChat)的
 * 统一响应基类,包含调用结果、耗时、元数据与原始响应快照。
 * <p>
 * 子类通过 {@code #succeed(Object)} / {@code #failed(String, String)} 工厂方法构建实例,
 * 保证 {@code success} / {@code time} / {@code duration} 等公共字段自动填充。
 *
 * @param <T> 子类自身类型(CRTP),使工厂方法返回子类类型而非基类
 * @author richie696
 * @since 1.0.0
 */
@Data
public class AiModelResponse<T extends AiModelResponse<T>> {

    /** 是否成功（true = 调用成功且结果可用）。 */
    protected boolean success;

    /** 错误码（成功时为 null）。 */
    protected String errorCode;

    /** 错误消息（成功时为 null）。 */
    protected String errorMessage;

    /** ISO-8601 时间戳,记录调用结束时间。 */
    protected String time;

    /** 调用耗时(毫秒),记录从发起到收到完整响应的时间。 */
    protected long duration;

    /** 扩展元数据,由各能力层或具体厂商按需注入。 */
    protected Map<String, Object> metadata;

    /** 原始响应 JSON（用于调试 / 审计,生产环境按需开启）。 */
    protected String rawResponse;

    /**
     * 构建成功响应。
     *
     * @param <T>  子类类型
     * @param self 子类实例（已由子类 {@code new} 并设置专有字段）
     * @param clock 时钟（外部注入以便单测锁时间,生产传 {@link Clock#systemUTC()})
     * @return 填充好的子类实例
     */
    @SuppressWarnings("unchecked")
    public static <T extends AiModelResponse<T>> T succeed(T self, Clock clock) {
        Instant now = Instant.now(clock);
        self.success = true;
        self.time = now.toString();
        self.duration = 0;
        return self;
    }

    /**
     * 构建失败响应。
     *
     * @param <T>         子类类型
     * @param self        子类实例
     * @param errorCode   错误码（非 null）
     * @param errorMessage 错误描述
     * @param clock       时钟
     * @return 填充好的子类实例
     */
    @SuppressWarnings("unchecked")
    public static <T extends AiModelResponse<T>> T failed(T self, String errorCode, String errorMessage, Clock clock) {
        Instant now = Instant.now(clock);
        self.success = false;
        self.errorCode = errorCode;
        self.errorMessage = errorMessage;
        self.time = now.toString();
        self.duration = 0;
        return self;
    }

    /**
     * 绑定实际耗时。
     *
     * @param durationMs 本次调用的真实耗时(毫秒)
     * @return 自身,方便链式调用
     */
    @SuppressWarnings("unchecked")
    public T withDuration(long durationMs) {
        this.duration = durationMs;
        return (T) this;
    }

    /**
     * 绑定原始响应快照。
     *
     * @param rawResponse 原始响应 JSON 字符串
     * @return 自身,方便链式调用
     */
    @SuppressWarnings("unchecked")
    public T withRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
        return (T) this;
    }

    /**
     * 绑定扩展元数据。
     *
     * @param metadata 元数据 Map
     * @return 自身,方便链式调用
     */
    @SuppressWarnings("unchecked")
    public T withMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        return (T) this;
    }
}
