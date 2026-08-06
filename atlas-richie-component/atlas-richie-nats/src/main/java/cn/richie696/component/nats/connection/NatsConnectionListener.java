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
package cn.richie696.component.nats.connection;

import io.nats.client.Connection;

/**
 * NATS 连接事件监听接口
 *
 * <p>组件内部使用，由 {@link NatsConnectionManager} 注册到 jnats 驱动。
 * 用户如需监听连接事件，可通过 {@link NatsConnectionManager#addConnectionListener} 注册。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public interface NatsConnectionListener {

    /**
     * 连接已建立（或重连完成）时回调。
     *
     * <p>对应 jnats 驱动层 {@code CONNECTED} / {@code RECONNECTED} / {@code RESUBSCRIBED} 事件：
     * 首次建连成功、自动重连成功以及重连后订阅恢复都会触发本回调。</p>
     *
     * @param connection 当前活跃的 jnats {@link Connection}，可用于查询服务端信息或直接收发消息
     */
    default void onConnected(Connection connection) {
    }

    /**
     * 与服务端连接断开时回调（可能由网络抖动、服务端重启等触发）。
     *
     * <p>对应 jnats 驱动层 {@code DISCONNECTED} 事件。若 jnats 启动了自动重连，
     * 通常紧随其后会触发 {@link #onReconnecting(Connection)}；若未启用重连，
     * 则连接进入不可用状态，需由调用方决定后续策略。</p>
     *
     * @param connection 断开时的 jnats {@link Connection}（已不可用于业务收发）
     */
    default void onDisconnected(Connection connection) {
    }

    /**
     * 正在尝试重新建立与服务端的连接时回调。
     *
     * <p>对应 jnats 驱动层隐含的重连过程。该回调仅作状态通知，期间连接不可用，
     * 调用方不应在此回调内执行业务收发。</p>
     *
     * @param connection 处于重连中的 jnats {@link Connection}
     */
    default void onReconnecting(Connection connection) {
    }

    /**
     * 连接已完全关闭时回调（通常发生在 {@code drain} 完成或强制 {@code close} 之后）。
     *
     * <p>对应 jnats 驱动层 {@code CLOSED} 事件。该事件触发后连接不可再被使用，
     * 且不会再自动重连。</p>
     *
     * @param connection 已关闭的 jnats {@link Connection}
     */
    default void onClosed(Connection connection) {
    }

    /**
     * 连接发生异步错误时回调。
     *
     * <p>来源包括：协议错误、流控触发、服务端主动通知错误等。实现方应当仅做日志/告警，
     * 不要在此抛出异常（异常会被 {@link NatsConnectionManager} 吞掉以保护其他监听器）。</p>
     *
     * @param connection 发生错误时的 jnats {@link Connection}（可能仍处于已连接状态）
     * @param error      jnats 抛出的具体异常或错误描述
     */
    default void onError(Connection connection, Exception error) {
    }
}
