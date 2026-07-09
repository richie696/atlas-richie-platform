/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.redis.streammq.stream;

/**
 * 事件发布失败原因枚举
 *
 * <p>用于描述 Redis Stream 事件总线在发布消息时可能遇到的失败类型，
 * 便于调用方根据失败原因采取差异化处理（如重试、降级或告警）。
 *
 * <ul>
 *   <li>SINK_TERMINATED：发布器已终止，需重新初始化</li>
 *   <li>BUFFER_OVERFLOW：缓冲区溢出，建议退避重试</li>
 *   <li>OPERATION_CANCELLED：操作被取消</li>
 *   <li>NON_SERIALIZED_ACCESS：非串行化访问冲突</li>
 *   <li>UNKNOWN_ERROR：未知错误</li>
 *   <li>EXCEPTION：发布过程中抛出异常</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-09-16
 */
public enum PublishFailureReason {
    /**
     * 发布器已终止，需重新初始化
     */
    SINK_TERMINATED,
    /**
     * 缓冲区溢出，建议退避重试
     */
    BUFFER_OVERFLOW,
    /**
     * 操作被取消
     */
    OPERATION_CANCELLED,
    /**
     * 非串行化访问冲突
     */
    NON_SERIALIZED_ACCESS,
    /**
     * 未知错误
     */
    UNKNOWN_ERROR,
    /**
     * 发布过程中抛出异常
     */
    EXCEPTION
}


