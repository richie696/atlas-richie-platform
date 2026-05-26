package com.richie.component.mqtt.beans;

/**
 * MQTT连接状态枚举
 * 与Android端保持一致
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-27
 */
public enum ConnectionState {
    /**
     * 已连接
     */
    CONNECTED,

    /**
     * 未连接
     */
    DISCONNECTED,

    /**
     * 异常断开连接
     */
    ABNORMAL_DISCONNECT,

    /**
     * 会话过期
     */
    SESSION_EXPIRED,

    /**
     * 连接中
     */
    CONNECTING, 

    /**
     * 网络恢复
     */
    NETWORK_RECOVERED,

    /**
     * 连接失败
     */
    CONNECTION_FAILED,

    /**
     * 连接超时
     */
    CONNECTION_TIMEOUT,

    /**
     * 网络质量差
     */
    POOR_NETWORK;

    /**
     * 检查当前状态是否在指定的状态列表中
     *
     * @param states 要检查的状态列表
     * @return 如果当前状态在列表中返回true，否则返回false
     */
    public boolean in(ConnectionState... states) {
        for (ConnectionState state : states) {
            if (this == state) {
                return true;
            }
        }
        return false;
    }
}
