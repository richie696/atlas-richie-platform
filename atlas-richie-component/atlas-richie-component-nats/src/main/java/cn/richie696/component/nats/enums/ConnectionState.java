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
package cn.richie696.component.nats.enums;

/**
 * NATS 连接状态枚举
 *
 * @author richie696
 * @since 1.0.0
 */
public enum ConnectionState {

    /**
     * 已连接：连接可用、可正常发布与订阅。
     */
    CONNECTED,

    /**
     * 正在重连：底层连接断开但 NATS 客户端在自动尝试重连。
     */
    RECONNECTING,

    /**
     * 已断开：当前没有可用连接，发布/订阅请求会失败。
     */
    DISCONNECTED,

    /**
     * 已关闭：连接已显式关闭，不再自动重连。
     */
    CLOSED;

    /**
     * 判断当前状态是否为已连接。
     *
     * @return 当前状态为 {@link #CONNECTED} 时返回 {@code true}
     */
    public boolean isConnected() {
        return this == CONNECTED;
    }
}
