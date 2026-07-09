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
package com.richie.component.nats.connection;

import com.richie.component.nats.config.NatsProperties;
import com.richie.component.nats.enums.ConnectionState;
import com.richie.component.nats.exception.NatsConnectionException;
import io.nats.client.Connection;
import io.nats.client.ConsumerContext;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.StreamContext;
import io.nats.client.impl.Headers;
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
    private final List<NatsConnectionListener> listeners = new CopyOnWriteArrayList<>();
    private volatile Connection connection;
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
     * 获取当前连接状态
     */
    public ConnectionState getState() {
        return state;
    }

    /**
     * 注册连接事件监听器
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

    private Connection connect() {
        try {
            Options.Builder builder = properties.toOptionsBuilder();

            // 注册 jnats 驱动层连接监听器，转发到组件 NatsConnectionListener
            builder.connectionListener(new io.nats.client.ConnectionListener() {
                @Override
                public void connectionEvent(Connection conn, Events type) {
                    handleDriverConnectionEvent(conn, type);
                }
            });

            // 注册 jnats 驱动层错误监听器
            builder.errorListener(new io.nats.client.ErrorListener() {
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

    private void handleDriverConnectionEvent(Connection conn, io.nats.client.ConnectionListener.Events type) {
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

    private void updateState(ConnectionState newState) {
        var oldState = this.state;
        this.state = newState;
        if (oldState != newState) {
            log.info("NATS connection state: {} → {}", oldState, newState);
        }
    }

    private void safeInvoke(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("NATS connection listener callback error", e);
        }
    }
}
