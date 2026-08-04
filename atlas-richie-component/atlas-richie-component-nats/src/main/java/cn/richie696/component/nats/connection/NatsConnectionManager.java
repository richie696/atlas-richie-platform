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

import cn.richie696.component.nats.config.NatsProperties;
import cn.richie696.component.nats.enums.ConnectionState;
import cn.richie696.component.nats.exception.NatsConnectionException;
import io.nats.client.*;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * NATS 连接管理器
 *
 * <p>核心职责：连接创建、StreamContext / ConsumerContext 获取、状态查询、优雅关闭。
 * NATS 采用单 TCP 连接多路复用模型，一个连接承载所有订阅/发布/RPC 操作。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class NatsConnectionManager {

    private final NatsProperties properties;
    // CopyOnWriteArrayList：监听器可能在驱动回调线程中被迭代，使用 CoW 避免 ConcurrentModificationException，
    // 写入开销可接受（注册监听器是低频操作）。
    private final List<NatsConnectionListener> listeners = new CopyOnWriteArrayList<>();
    // volatile：跨线程可见（驱动回调线程、业务线程、关闭线程均可读写），单次写、多次读，无需锁。
    private volatile Connection connection;
    // 状态机桥接字段：将 jnats 驱动层事件聚合为本组件统一对外的 ConnectionState，
    // 供业务方在无需直接持有 jnats Connection 的前提下做健康检查/熔断决策。
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;

    public NatsConnectionManager(NatsProperties properties) {
        this.properties = properties;
    }

    /**
     * 获取 NATS 连接（懒初始化，首次调用时创建）
     *
     * @return 活跃连接
     * @throws NatsConnectionException 连接失败时抛出
     */
    public synchronized Connection getConnection() {
        if (connection != null && connection.getStatus() == Connection.Status.CONNECTED) {
            return connection;
        }
        return connect();
    }

    /**
     * 获取 StreamContext（新 API，替代旧版 JetStreamManagement）
     *
     * @param streamName Stream 名称
     * @return StreamContext
     */
    public StreamContext getStreamContext(String streamName) {
        try {
            return getConnection().getStreamContext(streamName);
        } catch (Exception e) {
            throw new NatsConnectionException("Failed to get StreamContext for stream: " + streamName, e);
        }
    }

    /**
     * 获取 ConsumerContext（新 API）
     *
     * @param streamName   Stream 名称
     * @param consumerName Consumer 名称
     * @return ConsumerContext
     */
    public ConsumerContext getConsumerContext(String streamName, String consumerName) {
        try {
            return getConnection().getConsumerContext(streamName, consumerName);
        } catch (Exception e) {
            throw new NatsConnectionException(
                    "Failed to get ConsumerContext for stream: " + streamName
                            + ", consumer: " + consumerName, e);
        }
    }

    /**
     * 获取当前连接状态。
     *
     * <p>返回的是本组件聚合的 {@link ConnectionState}，而非 jnats 原生的连接状态枚举，
     * 调用方无需感知 jnats 内部事件类别。</p>
     *
     * @return 当前连接状态，永不为 {@code null}
     */
    public ConnectionState getState() {
        return state;
    }

    /**
     * 注册连接事件监听器。
     *
     * <p>监听器会在驱动事件桥接线程中被同步回调；为避免阻塞后续监听器，
     * 监听器实现应保持轻量，耗时操作请自行异步化。</p>
     *
     * @param listener 自定义监听器，允许为 {@code null}（将被静默忽略）
     */
    public void addConnectionListener(NatsConnectionListener listener) {
        listeners.add(listener);
    }

    /**
     * 优雅关闭连接（先 drain 再 close）
     *
     * @param drainTimeout drain 超时
     */
    public void shutdown(Duration drainTimeout) {
        if (connection == null) {
            return;
        }
        try {
            log.info("NATS connection shutting down, drain timeout: {}", drainTimeout);
            connection.drain(drainTimeout);
        } catch (Exception e) {
            log.warn("NATS drain failed, force closing", e);
        } finally {
            try {
                connection.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("NATS connection close interrupted");
            }
            updateState(ConnectionState.CLOSED);
        }
    }

    // ===== 内部方法 =====

    /**
     * 懒初始化创建 jnats {@link Connection}，并装配驱动层 ConnectionListener/ErrorListener。
     *
     * <p>设计要点：
     * <ul>
     *   <li>连接事件做归一化处理（{@link #handleDriverConnectionEvent}），将 jnats 多事件类型
     *       映射为本组件 {@link ConnectionState}；</li>
     *   <li>错误事件统一通过 {@link #safeInvoke} 派发，避免单个监听器抛异常阻塞其它监听器；</li>
     *   <li>InterruptedException 显式恢复中断标志，防止上游遮蔽线程中断语义。</li>
     * </ul>
     *
     * @return 已建立并标记为 {@link ConnectionState#CONNECTED} 的连接
     * @throws NatsConnectionException 当 jnats 在建连过程中抛出 IO/中断异常时
     */
    private Connection connect() {
        try {
            Options.Builder builder = properties.toOptionsBuilder();

            // 注册 jnats 驱动层连接监听器，转发到组件 NatsConnectionListener
            builder.connectionListener(new ConnectionListener() {
                @Override
                public void connectionEvent(Connection conn, Events type) {
                    handleDriverConnectionEvent(conn, type);
                }
            });

            // 注册 jnats 驱动层错误监听器
            builder.errorListener(new ErrorListener() {
                @Override
                public void errorOccurred(Connection conn, String error) {
                    log.error("NATS error: {}", error);
                    listeners.forEach(l -> safeInvoke(() ->
                            l.onError(conn, new NatsConnectionException(error))));
                }

                @Override
                public void exceptionOccurred(Connection conn, Exception exp) {
                    log.error("NATS exception", exp);
                    listeners.forEach(l -> safeInvoke(() -> l.onError(conn, exp)));
                }
            });

            Options options = builder.build();
            log.info("Connecting to NATS server: {}", properties.getServer());
            connection = Nats.connect(options);
            updateState(ConnectionState.CONNECTED);
            log.info("NATS connection established: {}", connection.getConnectedUrl());
            return connection;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new NatsConnectionException("Failed to connect to NATS server: " + properties.getServer(), e);
        }
    }

    /**
     * 将 jnats 驱动层连接事件归一化后转译为本组件 {@link ConnectionState} 并广播给业务监听器。
     *
     * <p>映射策略：
     * <ul>
     *   <li>{@code CONNECTED} / {@code RESUBSCRIBED} / {@code RECONNECTED} → {@link ConnectionState#CONNECTED}，
     *       业务上等价"已可用"；</li>
     *   <li>{@code DISCONNECTED} → {@link ConnectionState#DISCONNECTED}；</li>
     *   <li>{@code CLOSED} → {@link ConnectionState#CLOSED}，连接生命周期结束。</li>
     * </ul>
     * 其它事件仅 debug 日志，避免向业务侧暴露 jnats 内部事件噪音。
     *
     * @param conn  事件触发的 jnats 连接
     * @param type  jnats 驱动事件类型
     */
    private void handleDriverConnectionEvent(Connection conn, ConnectionListener.Events type) {
        switch (type) {
            case CONNECTED, RESUBSCRIBED -> {
                updateState(ConnectionState.CONNECTED);
                listeners.forEach(l -> safeInvoke(() -> l.onConnected(conn)));
            }
            case DISCONNECTED -> {
                updateState(ConnectionState.DISCONNECTED);
                listeners.forEach(l -> safeInvoke(() -> l.onDisconnected(conn)));
            }
            case RECONNECTED -> {
                updateState(ConnectionState.CONNECTED);
                listeners.forEach(l -> safeInvoke(() -> l.onConnected(conn)));
            }
            case CLOSED -> {
                updateState(ConnectionState.CLOSED);
                listeners.forEach(l -> safeInvoke(() -> l.onClosed(conn)));
            }
            default -> log.debug("NATS connection event: {}", type);
        }
    }

    /**
     * 单调更新内部 {@link #state} 字段，并在状态实际发生变化时记录状态转移日志。
     *
     * <p>为何集中在此：{@link #state} 是状态机桥接的单一入口，
     * 所有驱动事件/关闭事件都需经过该方法，确保状态变化可被审计。</p>
     *
     * @param newState 新的连接状态
     */
    private void updateState(ConnectionState newState) {
        var oldState = this.state;
        this.state = newState;
        if (oldState != newState) {
            log.info("NATS connection state: {} → {}", oldState, newState);
        }
    }

    /**
     * 监听器隔离执行：捕获监听器回调中的任何异常，避免单个监听器抛异常中断后续监听器派发。
     *
     * <p>监听器来自业务方且可能包含 I/O、远程调用等不可控代码；
     * 若不做隔离，任意一个监听器抛 RuntimeException 都会中断其它监听器的事件派发。</p>
     *
     * @param action 待执行的监听器回调动作
     */
    private void safeInvoke(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("NATS connection listener callback error", e);
        }
    }
}
