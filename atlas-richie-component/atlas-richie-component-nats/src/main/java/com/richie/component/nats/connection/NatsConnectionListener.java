package com.richie.component.nats.connection;

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

    default void onConnected(Connection connection) {}

    default void onDisconnected(Connection connection) {}

    default void onReconnecting(Connection connection) {}

    default void onClosed(Connection connection) {}

    default void onError(Connection connection, Exception error) {}
}
