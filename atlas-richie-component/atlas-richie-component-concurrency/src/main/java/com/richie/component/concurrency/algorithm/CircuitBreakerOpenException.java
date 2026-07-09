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
package com.richie.component.concurrency.algorithm;

import java.io.Serial;

/**
 * 熔断器开启异常 —— 当 {@link CircuitBreaker} 处于 OPEN 状态时执行被立即拒绝时抛出。
 *
 * <p>这是非受检异常（{@link RuntimeException}），调用方可选择：</p>
 * <ul>
 *   <li>捕获并返回 fallback 值（推荐用于关键业务链路）</li>
 *   <li>沿调用栈向上传播，让上层做降级或告警处理</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
public class CircuitBreakerOpenException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建一个带有指定详细消息的熔断器异常。
     *
     * @param message 详细消息
     */
    public CircuitBreakerOpenException(String message) {
        super(message);
    }

    /**
     * 创建一个带有详细消息和根因的熔断器异常。
     *
     * @param message 详细消息
     * @param cause   根因（通常为底层故障异常）
     */
    public CircuitBreakerOpenException(String message, Throwable cause) {
        super(message, cause);
    }
}
