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
package com.richie.component.http.core;

/**
 * SSE 连接生命周期回调。
 * <p>
 * 所有方法均为 {@code default}，调用方按需覆盖即可，避免在业务侧出现大量空方法体。
 * 回调通常在底层 Provider 的 IO 线程中触发，{@link #onEvent} 中的业务逻辑
 * 需要自行保证线程安全，长耗时操作应转交业务线程池。
 *
 * <h2>典型用法</h2>
 * <pre>{@code
 *   SseListener listener = new SseListener() {
 *       @Override public void onEvent(SseConnection conn, SseEvent event) {
 *           log.info("event id={} data={}", event.id(), event.data());
 *       }
 *       @Override public void onFailure(SseConnection conn, Throwable cause) {
 *           log.error("sse failure", cause);
 *       }
 *   };
 * }</pre>
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
public interface SseListener {

    /**
     * 连接建立、首字节到达时触发。
     * <p>
     * 该回调触发后即可通过 {@link SseConnection#statusCode()} /
     * {@link SseConnection#headers()} 获取响应元信息。
     *
     * @param connection 当前 SSE 连接
     */
    default void onOpen(SseConnection connection) {
    }

    /**
     * 每解析到一个完整的 SSE 事件时触发一次。
     *
     * @param connection 当前 SSE 连接
     * @param event      已解析的 SSE 事件
     */
    default void onEvent(SseConnection connection, SseEvent event) {
    }

    /**
     * 服务端主动关闭流时触发（例如发送完最后一个事件后关闭）。
     *
     * @param connection 当前 SSE 连接
     */
    default void onClosed(SseConnection connection) {
    }

    /**
     * 连接异常、网络中断、SSE 协议解析失败等场景触发。
     * <p>
     * 实现方需自行判断是否需要重连；本组件不内置自动重连策略，避免与业务幂等逻辑冲突。
     *
     * @param connection 当前 SSE 连接
     * @param cause      失败原因
     */
    default void onFailure(SseConnection connection, Throwable cause) {
    }

}