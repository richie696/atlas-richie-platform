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
package com.richie.component.mqtt.generator.impl;

import com.richie.contract.constant.GlobalConstants;
import com.richie.component.mqtt.generator.ClientIdRuler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 默认客户端ID生成规则实现
 * <p>
 * 基于硬件标识符、时间戳和随机数生成唯一的客户端ID。
 * 格式：前缀-版本-硬件指纹-随机串
 * <p>
 * <strong>生成规则：</strong>
 * <ul>
 *   <li>格式：{前缀}-{版本}-{硬件指纹}-{随机串}</li>
 *   <li>前缀：固定值 "RY"（写死在代码中）</li>
 *   <li>版本：框架版本号</li>
 *   <li>硬件指纹：基于 MAC 地址的 SHA-1 哈希值，8 位 16 进制</li>
 *   <li>随机串：6 位随机字符 + 3 位时间戳 + 3 位序列号</li>
 * </ul>
 * <p>
 * <strong>示例：</strong>
 * <ul>
 *   <li>RY-460-ABCD1234-abc123def456</li>
 * </ul>
 * <p>
 * <strong>注意：</strong>
 * <ul>
 *   <li>此实现不限制 ClientId 长度，可能超过 MQTT 5.0 规范的 23 字节限制</li>
 *   <li>如需符合 MQTT 5.0 规范，请使用 {@code mqtt5ClientIdRuler} 实现</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-13
 */
@Slf4j
@Component("defaultClientIdRuler")
public class DefaultClientIdRuler implements ClientIdRuler {

    /**
     * 客户端ID前缀（固定值）
     */
    private static final String CLIENT_ID_PREFIX = "RY";
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final AtomicLong SEQUENCE = new AtomicLong(0);

    @Override
    public String getClientId() {
        String hardwareId = getHardwareIdentifierHash();
        // clientId格式: 前缀-版本-硬件指纹-随机串
        return "%s-%d-%s-%s".formatted(
                CLIENT_ID_PREFIX,
                GlobalConstants.FRAMEWORK_VERSION,
                hardwareId,
                generateRandomClientId()
        );
    }

    /**
     * 获取本机第一个有效MAC地址的SHA-1 hash的前8位，作为硬件指纹
     *
     * @return 返回硬件指纹
     */
    private String getHardwareIdentifierHash() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni != null && !ni.isLoopback() && ni.getHardwareAddress() != null) {
                    byte[] mac = ni.getHardwareAddress();
                    MessageDigest md = MessageDigest.getInstance("SHA-1");
                    byte[] hash = md.digest(mac);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < 4; i++) { // 取前4字节，8位16进制
                        sb.append(String.format("%02X", hash[i]));
                    }
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get hardware identifier: {}", e.getMessage());
        }
        return "UNKNOWN";
    }

    /**
     * 生成随机的clientId部分，包含6位随机字符+3位时间戳+3位序列号
     *
     * @return 返回随机clientId
     */
    private String generateRandomClientId() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHANUMERIC.charAt(SECURE_RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        String timestampHex = Long.toHexString(System.currentTimeMillis() % 0xFFF);
        sb.append(String.format("%3s", timestampHex).replace(' ', '0'));
        String sequenceHex = Long.toHexString(SEQUENCE.incrementAndGet() % 0xFFF);
        sb.append(String.format("%3s", sequenceHex).replace(' ', '0'));
        return sb.toString();
    }
}
