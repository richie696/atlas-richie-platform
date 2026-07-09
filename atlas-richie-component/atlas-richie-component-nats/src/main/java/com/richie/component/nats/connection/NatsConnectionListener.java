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
