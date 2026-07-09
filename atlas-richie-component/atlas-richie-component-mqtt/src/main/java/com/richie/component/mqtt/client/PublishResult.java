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
package com.richie.component.mqtt.client;

/**
 * MQTT事件发布结果
 * <p>
 * 用于表示事件发布的成功或失败状态，包含详细的失败原因和异常信息。
 *
 * @param <T> 事件类型
 * @author richie696
 * @version 1.0
 * @since 2025-08-13
 */
public class PublishResult<T> {

    private final T event;
    private final boolean success;
    private final PublishFailureReason failureReason;
    private final Throwable exception;
    private final long timestamp;

    private PublishResult(T event, boolean success, PublishFailureReason failureReason,
                         Throwable exception, long timestamp) {
        this.event = event;
        this.success = success;
        this.failureReason = failureReason;
        this.exception = exception;
        this.timestamp = timestamp;
    }

    /**
     * 创建成功结果
     *
     * @param event 发布的事件
     * @param <T> 事件类型
     * @return 成功结果
     */
    public static <T> PublishResult<T> success(T event) {
        return new PublishResult<>(event, true, null, null, System.currentTimeMillis());
    }

    /**
     * 创建失败结果
     *
     * @param event 发布的事件
     * @param failureReason 失败原因
     * @param <T> 事件类型
     * @return 失败结果
     */
    public static <T> PublishResult<T> failed(T event, PublishFailureReason failureReason) {
        return new PublishResult<>(event, false, failureReason, null, System.currentTimeMillis());
    }

    /**
     * 创建失败结果（带异常）
     *
     * @param event 发布的事件
     * @param failureReason 失败原因
     * @param exception 异常信息
     * @param <T> 事件类型
     * @return 失败结果
     */
    public static <T> PublishResult<T> failed(T event, PublishFailureReason failureReason, Throwable exception) {
        return new PublishResult<>(event, false, failureReason, exception, System.currentTimeMillis());
    }

    // ==================== Getter 方法 ====================

    /**
     * 获取发布的事件
     *
     * @return 发布的事件
     */
    public T getEvent() {
        return event;
    }

    /**
     * 检查发布是否成功
     *
     * @return 如果发布成功返回true，否则返回false
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 检查发布是否失败
     *
     * @return 如果发布失败返回true，否则返回false
     */
    public boolean isFailed() {
        return !success;
    }

    /**
     * 获取失败原因
     *
     * @return 失败原因，如果成功则为null
     */
    public PublishFailureReason getFailureReason() {
        return failureReason;
    }

    /**
     * 获取异常信息
     *
     * @return 异常信息，如果没有异常则为null
     */
    public Throwable getException() {
        return exception;
    }

    /**
     * 获取时间戳
     *
     * @return 发布结果的时间戳（毫秒）
     */
    public long getTimestamp() {
        return timestamp;
    }

    // ==================== 便捷方法 ====================

    /**
     * 检查是否是缓冲区溢出
     *
     * @return 是否是缓冲区溢出
     */
    public boolean isBufferOverflow() {
        return failureReason == PublishFailureReason.BUFFER_OVERFLOW;
    }

    /**
     * 检查是否是Sink终止
     *
     * @return 是否是Sink终止
     */
    public boolean isSinkTerminated() {
        return failureReason == PublishFailureReason.SINK_TERMINATED;
    }

    /**
     * 检查是否是操作被取消
     *
     * @return 是否是操作被取消
     */
    public boolean isOperationCancelled() {
        return failureReason == PublishFailureReason.OPERATION_CANCELLED;
    }

    /**
     * 检查是否是非序列化访问
     *
     * @return 是否是非序列化访问
     */
    public boolean isNonSerializedAccess() {
        return failureReason == PublishFailureReason.NON_SERIALIZED_ACCESS;
    }

    /**
     * 检查是否是异常发生
     *
     * @return 是否是异常发生
     */
    public boolean isException() {
        return failureReason == PublishFailureReason.EXCEPTION;
    }

    @Override
    public String toString() {
        if (success) {
            return String.format("PublishResult{success=true, event=%s, timestamp=%d}",
                event, timestamp);
        } else {
            return String.format("PublishResult{success=false, event=%s, failureReason=%s, " +
                "exception=%s, timestamp=%d}", event, failureReason,
                exception != null ? exception.getMessage() : "null", timestamp);
        }
    }
}
