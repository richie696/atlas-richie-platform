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
package com.richie.component.mqtt.enums;

/**
 * MQTT 连接协议枚举
 * <p>
 * 定义 MQTT 客户端连接服务器时使用的协议类型。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-04
 */
public enum MqttProtocolEnum {

    /**
     * 标准 TCP 连接
     * <p>
     * 使用标准的 TCP 协议进行连接，不加密。
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>内网环境</li>
     *   <li>开发测试环境</li>
     *   <li>不需要加密的场景</li>
     * </ul>
     * <p>
     * <strong>默认端口：</strong>1883
     */
    TCP,

    /**
     * SSL/TLS 加密连接
     * <p>
     * 使用 SSL/TLS 协议进行加密连接，支持两种模式：
     * <ul>
     *   <li><strong>X.509证书认证模式</strong>：当配置了完整的证书信息时，使用双向SSL认证</li>
     *   <li><strong>简单TLS模式</strong>：当未配置证书信息时，使用简单的TLS连接（仅用户名密码认证）</li>
     * </ul>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>生产环境</li>
     *   <li>安全要求高的场景</li>
     *   <li>需要加密传输的场景</li>
     * </ul>
     * <p>
     * <strong>默认端口：</strong>8883
     */
    SSL,

    /**
     * TLS 加密连接（简单模式）
     * <p>
     * 使用 TLS 协议进行加密连接，仅通过用户名密码认证，不进行客户端证书验证。
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>腾讯云 MQTT 等场景</li>
     *   <li>需要加密但不需要客户端证书的场景</li>
     *   <li>简单 TLS 连接</li>
     * </ul>
     * <p>
     * <strong>默认端口：</strong>8883
     * <p>
     * <strong>注意：</strong>此协议与 {@link #SSL} 在简单 TLS 模式下功能相同，但语义上更明确表示仅用户名密码认证。
     */
    TLS,

    /**
     * WebSocket 连接
     * <p>
     * 使用 WebSocket 协议进行连接，不加密。
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>浏览器环境</li>
     *   <li>防火墙限制 TCP 连接的场景</li>
     *   <li>需要通过 HTTP 代理的场景</li>
     * </ul>
     * <p>
     * <strong>默认端口：</strong>8083
     */
    WS,

    /**
     * WebSocket over SSL 连接
     * <p>
     * 使用 WebSocket over SSL 协议进行加密连接。
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>浏览器环境（需要加密）</li>
     *   <li>防火墙限制但需要加密的场景</li>
     *   <li>需要通过 HTTPS 代理的场景</li>
     * </ul>
     * <p>
     * <strong>默认端口：</strong>8084
     */
    WSS;

    /**
     * 判断是否需要 SSL/TLS 加密
     * <p>
     * 检查当前协议是否需要 SSL/TLS 加密连接。
     *
     * @return 如果需要 SSL/TLS 加密返回 true，否则返回 false
     */
    public boolean needsSsl() {
        return this == SSL || this == TLS || this == WSS;
    }

    /**
     * 获取协议字符串值（小写）
     * <p>
     * 返回协议的小写字符串表示，用于构建连接 URI。
     *
     * @return 协议的小写字符串值（如 "tcp", "ssl", "tls", "ws", "wss"）
     */
    public String getValue() {
        return this.name().toLowerCase();
    }

    /**
     * 根据字符串值获取协议枚举
     * <p>
     * 支持不区分大小写的字符串匹配。
     *
     * @param value 协议字符串值（如 "tcp", "ssl", "tls", "ws", "wss"）
     * @return 对应的协议枚举，如果未找到则返回 null
     */
    public static MqttProtocolEnum fromValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 根据字符串值获取协议枚举，如果未找到则返回默认值
     * <p>
     * 支持不区分大小写的字符串匹配。
     *
     * @param value        协议字符串值（如 "tcp", "ssl", "tls", "ws", "wss"）
     * @param defaultValue 默认值，如果未找到则返回此值
     * @return 对应的协议枚举，如果未找到则返回默认值
     */
    public static MqttProtocolEnum fromValue(String value, MqttProtocolEnum defaultValue) {
        MqttProtocolEnum protocol = fromValue(value);
        return protocol != null ? protocol : defaultValue;
    }
}
