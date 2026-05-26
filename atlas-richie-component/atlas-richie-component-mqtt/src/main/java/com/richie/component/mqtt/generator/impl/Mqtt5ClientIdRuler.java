package com.richie.component.mqtt.generator.impl;

import com.richie.contract.constant.GlobalConstants;
import com.richie.component.mqtt.generator.ClientIdRuler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MQTT 5.0 规范客户端ID生成规则实现
 * <p>
 * 基于 MQTT 5.0 官方规范生成客户端ID，严格遵循长度限制。
 * <p>
 * <strong>MQTT 5.0 规范要求：</strong>
 * <ul>
 *   <li>ClientId 必须是 UTF-8 编码的字符串</li>
 *   <li>长度必须在 1 到 23 个字节之间</li>
 *   <li>必须保证唯一性</li>
 * </ul>
 * <p>
 * <strong>生成规则：</strong>
 * <ul>
 *   <li>格式：{版本}{硬件指纹}{随机码}</li>
 *   <li>版本：3 个字符（框架版本号，例如 "460"）</li>
 *   <li>硬件指纹：基于 MAC 地址的哈希值，8 个字符（Base64 编码的前 6 字节）</li>
 *   <li>随机码：12 个字符（字母和数字组合，确保唯一性）</li>
 *   <li>总长度：严格控制在 23 字节，符合 MQTT 5.0 规范</li>
 * </ul>
 * <p>
 * <strong>示例：</strong>
 * <ul>
 *   <li>460aBcDeFg123456789012（23 字节）</li>
 * </ul>
 * <p>
 * <strong>使用方式：</strong>
 * <p>
 * 在配置文件中指定使用此实现：
 * <pre>{@code
 * platform:
 *   component:
 *     mqtt:
 *       client-id-ruler: mqtt5ClientIdRuler
 * }</pre>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-04
 * @see <a href="https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901059">MQTT 5.0 Specification - Client Identifier</a>
 */
@Slf4j
@Component("mqtt5ClientIdRuler")
public class Mqtt5ClientIdRuler implements ClientIdRuler {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final AtomicLong SEQUENCE = new AtomicLong(0);
    private static final int MAX_CLIENT_ID_LENGTH = 23; // MQTT 5.0 规范：最大 23 字节
    private static final int MIN_CLIENT_ID_LENGTH = 1;  // MQTT 5.0 规范：最小 1 字节

    @Override
    public String getClientId() {
        String version = getVersionString();
        String hardwareFingerprint = getHardwareFingerprint();
        String randomCode = generateRandomCode();

        // 组合：版本 + 硬件指纹 + 随机码
        String clientId = version + hardwareFingerprint + randomCode;

        // 确保长度符合 MQTT 5.0 规范（1-23 字节）
        byte[] bytes = clientId.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_CLIENT_ID_LENGTH) {
            // 如果超过 23 字节，截断随机码部分
            int usedLength = version.getBytes(StandardCharsets.UTF_8).length
                    + hardwareFingerprint.getBytes(StandardCharsets.UTF_8).length;
            int maxRandomLength = MAX_CLIENT_ID_LENGTH - usedLength;
            if (maxRandomLength > 0) {
                randomCode = randomCode.substring(0, Math.min(randomCode.length(), maxRandomLength));
                clientId = version + hardwareFingerprint + randomCode;
            } else {
                // 极端情况：版本+硬件指纹已经超过 23 字节，只保留版本
                int remaining = MAX_CLIENT_ID_LENGTH - version.getBytes(StandardCharsets.UTF_8).length;
                if (remaining > 0) {
                    clientId = version + hardwareFingerprint.substring(0, Math.min(hardwareFingerprint.length(), remaining));
                } else {
                    clientId = version.substring(0, Math.min(version.length(), MAX_CLIENT_ID_LENGTH));
                }
            }
        } else if (bytes.length < MIN_CLIENT_ID_LENGTH) {
            // 如果小于 1 字节（理论上不会发生），添加随机字符
            clientId = version + "0";
        }

        // 最终验证
        byte[] finalBytes = clientId.getBytes(StandardCharsets.UTF_8);
        if (finalBytes.length < MIN_CLIENT_ID_LENGTH || finalBytes.length > MAX_CLIENT_ID_LENGTH) {
            log.warn("Generated ClientId length {} is out of range [1, 23], using fallback", finalBytes.length);
            // 使用备用方案：纯随机字符串
            return generateFallbackClientId();
        }

        return clientId;
    }

    /**
     * 获取版本号字符串
     * <p>
     * 从 GlobalConstants.FRAMEWORK_VERSION 获取框架版本号，格式化为 3 位数字字符串
     *
     * @return 3 位版本号字符串（例如 "460"）
     */
    private String getVersionString() {
        int version = GlobalConstants.FRAMEWORK_VERSION;
        // 格式化为 3 位数字，不足补 0
        return String.format("%03d", version % 1000);
    }

    /**
     * 获取硬件指纹
     * <p>
     * 基于 MAC 地址生成 8 个字符的硬件指纹（Base64 编码的哈希值前 6 字节）
     *
     * @return 8 个字符的硬件指纹
     */
    private String getHardwareFingerprint() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni != null && !ni.isLoopback() && ni.getHardwareAddress() != null) {
                    byte[] mac = ni.getHardwareAddress();
                    if (mac != null && mac.length >= 6) {
                        // 使用 SHA-256 哈希 MAC 地址
                        MessageDigest md = MessageDigest.getInstance("SHA-256");
                        byte[] hash = md.digest(mac);
                        // 取前 6 字节，Base64 编码后得到 8 个字符
                        byte[] fingerprint = new byte[6];
                        System.arraycopy(hash, 0, fingerprint, 0, 6);
                        String base64 = Base64.getEncoder().encodeToString(fingerprint);
                        // Base64 编码 6 字节 = 8 个字符，去除可能的填充符
                        return base64.substring(0, 8).replace("+", "A").replace("/", "B").replace("=", "C");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get hardware identifier: {}", e.getMessage());
        }
        // 如果无法获取硬件标识，使用随机字符串
        return generateRandomString(8);
    }

    /**
     * 生成随机码
     * <p>
     * 生成 12 个字符的随机码，包含字母和数字，确保唯一性
     * <p>
     * 长度计算：23 字节（总长度）- 3 字节（版本）- 8 字节（硬件指纹）= 12 字节
     * <p>
     * 生成策略：使用时间戳和序列号生成唯一标识，不足部分用随机字符补齐
     *
     * @return 12 个字符的随机码
     */
    private String generateRandomCode() {
        // 使用时间戳和序列号生成唯一标识
        long timestamp = System.currentTimeMillis();
        long sequence = SEQUENCE.incrementAndGet();
        
        // 组合时间戳和序列号，转换为字符串
        // 使用 Base36 编码（0-9, a-z），更紧凑
        String timestampStr = Long.toString(timestamp, 36);
        String sequenceStr = Long.toString(sequence, 36);
        String combined = timestampStr + sequenceStr;
        
        // 如果长度不足 12 个字符，用随机字符补齐
        if (combined.length() < 12) {
            String randomSuffix = generateRandomString(12 - combined.length());
            combined = combined + randomSuffix;
        } else if (combined.length() > 12) {
            // 如果长度超过 12 个字符，截断（保留时间戳部分，截断序列号部分）
            if (timestampStr.length() >= 12) {
                combined = timestampStr.substring(0, 12);
            } else {
                int remaining = 12 - timestampStr.length();
                combined = timestampStr + sequenceStr.substring(0, Math.min(remaining, sequenceStr.length()));
                // 如果还不够，用随机字符补齐
                if (combined.length() < 12) {
                    combined = combined + generateRandomString(12 - combined.length());
                }
            }
        }
        
        // 确保只包含字母和数字（Base36 字符集：0-9, a-z）
        return combined.toLowerCase().replaceAll("[^0-9a-z]", "0");
    }

    /**
     * 生成备用 ClientId（当主方法失败时使用）
     * <p>
     * 使用版本号 + 纯随机字符串，确保长度在 1-23 字节之间
     *
     * @return 备用 ClientId
     */
    private String generateFallbackClientId() {
        String version = getVersionString();
        byte[] versionBytes = version.getBytes(StandardCharsets.UTF_8);
        int remainingLength = MAX_CLIENT_ID_LENGTH - versionBytes.length;
        if (remainingLength <= 0) {
            return version.substring(0, Math.min(version.length(), MAX_CLIENT_ID_LENGTH));
        }
        String randomSuffix = generateRandomString(remainingLength);
        return version + randomSuffix;
    }

    /**
     * 生成随机字符串
     * <p>
     * 使用字母和数字，确保 URL 安全
     *
     * @param length 字符串长度
     * @return 随机字符串
     */
    private String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
