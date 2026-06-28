package com.richie.component.nats.enums;

/**
 * NATS 连接状态枚举
 *
 * @author richie696
 * @since 1.0.0
 */
public enum ConnectionState {

    CONNECTED,
    RECONNECTING,
    DISCONNECTED,
    CLOSED;

    /**
     * 判断当前状态是否为已连接
     */
    public boolean isConnected() {
        return this == CONNECTED;
    }
}
