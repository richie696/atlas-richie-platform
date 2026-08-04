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
package cn.richie696.component.nats.exception;

/**
 * NATS RPC 请求-响应异常
 *
 * <p>区分 Timeout（超时）和 NoResponders（无响应者）两种语义。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public class NatsRpcException extends NatsException {

    private final boolean timeout;
    private final boolean noResponders;

    /**
     * 完整字段构造 RPC 异常。
     *
     * @param message 异常描述
     * @param cause 触发本异常的根因
     * @param timeout 是否为超时场景
     * @param noResponders 是否为无响应者场景
     */
    public NatsRpcException(String message, Throwable cause, boolean timeout, boolean noResponders) {
        super(message, cause);
        this.timeout = timeout;
        this.noResponders = noResponders;
    }

    /**
     * 构建超时场景的 RPC 异常。
     *
     * @param subject 请求对应的 subject
     * @param cause 触发超时的根因
     * @return {@link #isTimeout()} 为 {@code true} 的 RPC 异常
     */
    public static NatsRpcException timeout(String subject, Throwable cause) {
        return new NatsRpcException("RPC request timed out for subject: " + subject, cause, true, false);
    }

    /**
     * 构建无响应者场景的 RPC 异常。
     *
     * @param subject 请求对应的 subject
     * @param cause 触发无响应者的根因
     * @return {@link #isNoResponders()} 为 {@code true} 的 RPC 异常
     */
    public static NatsRpcException noResponders(String subject, Throwable cause) {
        return new NatsRpcException("No responders available for subject: " + subject, cause, false, true);
    }

    /**
     * 构建其他失败场景的 RPC 异常。
     *
     * @param subject 请求对应的 subject
     * @param cause 触发异常的根因
     * @return 通用 RPC 失败异常
     */
    public static NatsRpcException other(String subject, Throwable cause) {
        return new NatsRpcException("RPC request failed for subject: " + subject, cause, false, false);
    }

    /**
     * 判断本异常是否为请求超时。
     *
     * @return 超时时返回 {@code true}
     */
    public boolean isTimeout() {
        return timeout;
    }

    /**
     * 判断本异常是否为无响应者。
     *
     * @return 无响应者时返回 {@code true}
     */
    public boolean isNoResponders() {
        return noResponders;
    }
}
