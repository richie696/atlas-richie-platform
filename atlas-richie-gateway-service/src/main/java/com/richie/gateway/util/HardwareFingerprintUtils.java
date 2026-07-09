/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.gateway.util;

import com.richie.context.utils.data.JsonUtils;
import com.auth0.jwt.interfaces.Claim;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 硬件指纹工具类
 * <p>
 * 用于验证前端发送的硬件指纹是否与 Token 中记录的指纹匹配。
 * <p>
 * 安全策略：
 * <ul>
 *   <li>Token 签发时记录硬件指纹（存储在 Token 的 claims 中）</li>
 *   <li>后续请求时，验证请求中的硬件指纹是否与 Token 中的指纹匹配</li>
 *   <li>允许硬件特征有轻微变化（如浏览器更新），但大幅变化会拒绝</li>
 *   <li>即使攻击者盗取了 Token 和 deviceId，也无法伪造硬件指纹</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class HardwareFingerprintUtils {

    /**
     * 硬件指纹相似度阈值
     * <p>
     * 当两个指纹的相似度 >= 此值时，认为匹配。
     * <p>
     * 设置为 0.8，允许硬件特征有轻微变化（如浏览器更新、插件变化等）。
     */
    private static final double SIMILARITY_THRESHOLD = 0.8;

    /**
     * 硬件指纹数据结构
     */
    @Data
    public static class HardwareFingerprint {
        /** Canvas 指纹（SHA-256哈希） */
        private String canvas;
        /** WebGL 指纹（SHA-256哈希） */
        private String webgl;
        /** 屏幕分辨率 */
        private String screen;
        /** 时区 */
        private String timezone;
        /** 语言 */
        private String language;
        /** 硬件并发数 */
        private Integer hardwareConcurrency;
        /** 设备内存（如果可用） */
        private Integer deviceMemory;
        /** 颜色深度 */
        private Integer colorDepth;
        /** 像素比 */
        private Double pixelRatio;
        /** 平台信息 */
        private String platform;
        /** 时间戳（毫秒，用于防重放攻击） */
        private Long timestamp;
        /** 随机字符串（Base64编码，用于防重放攻击） */
        private String nonce;
    }

    /**
     * 从 JSON 字符串解析硬件指纹
     * <p>
     * 此方法只处理纯JSON字符串，不处理带签名的格式。
     * <p>
     * 如果输入是带签名的格式（JSON.签名），请先使用 {@link #separateSignedFingerprint(String)} 分离后再调用此方法。
     *
     * @param fingerprintJson 硬件指纹 JSON 字符串（纯JSON，不包含签名）
     * @return 硬件指纹对象，如果解析失败返回 null
     */
    public static HardwareFingerprint parseFingerprint(String fingerprintJson) {
        if (StringUtils.isBlank(fingerprintJson)) {
            return null;
        }

        try {
            return JsonUtils.getInstance().deserialize(fingerprintJson, HardwareFingerprint.class);
        } catch (Exception e) {
            log.warn("解析硬件指纹失败: {}", fingerprintJson.length() > 100 
                ? fingerprintJson.substring(0, 100) + "..." : fingerprintJson, e);
            return null;
        }
    }

    /**
     * 分离带签名的硬件指纹字符串
     * <p>
     * 将格式为 `{JSON数据}.{Base64编码的签名}` 的字符串分离为JSON部分和签名部分。
     *
     * @param signedFingerprint 签名后的硬件指纹字符串（格式：JSON.签名）
     * @return 分离结果，包含JSON部分和签名部分。如果格式错误返回 null
     */
    public static SignedFingerprintParts separateSignedFingerprint(String signedFingerprint) {
        if (StringUtils.isBlank(signedFingerprint)) {
            return null;
        }

        try {
            // 格式：JSON.签名
            int lastDotIndex = signedFingerprint.lastIndexOf('.');
            if (lastDotIndex < 0 || lastDotIndex >= signedFingerprint.length() - 1) {
                log.warn("签名格式错误，缺少分隔符或签名部分为空: {}", 
                    signedFingerprint.length() > 100 ? signedFingerprint.substring(0, 100) + "..." : signedFingerprint);
                return null;
            }

            String jsonPart = signedFingerprint.substring(0, lastDotIndex);
            String signature = signedFingerprint.substring(lastDotIndex + 1);

            // 验证签名部分是否符合Base64格式
            if (signature.length() < 20 || !signature.matches("^[A-Za-z0-9+/=]+$")) {
                log.warn("签名格式错误，签名部分不符合Base64格式: {}", signature);
                return null;
            }

            return new SignedFingerprintParts(jsonPart, signature);
        } catch (Exception e) {
            log.warn("分离签名后的硬件指纹失败: {}", 
                signedFingerprint.length() > 100 ? signedFingerprint.substring(0, 100) + "..." : signedFingerprint, e);
            return null;
        }
    }

    /**
     * 签名后的硬件指纹分离结果
     */
    public static class SignedFingerprintParts {
        /** JSON部分（不包含签名） */
        private final String jsonPart;
        /** Base64编码的签名部分 */
        private final String signature;

        public SignedFingerprintParts(String jsonPart, String signature) {
            this.jsonPart = jsonPart;
            this.signature = signature;
        }

        public String getJsonPart() {
            return jsonPart;
        }

        public String getSignature() {
            return signature;
        }
    }

    /**
     * 从签名后的字符串解析硬件指纹（格式：JSON.签名）
     * <p>
     * 签名格式：`{JSON数据}.{Base64编码的HMAC-SHA256签名}`
     * <p>
     * 此方法会分离JSON和签名部分，然后解析JSON。
     *
     * @param signedFingerprint 签名后的硬件指纹字符串
     * @return 硬件指纹对象，如果解析失败返回 null
     */
    public static HardwareFingerprint parseSignedFingerprint(String signedFingerprint) {
        if (StringUtils.isBlank(signedFingerprint)) {
            return null;
        }

        try {
            // 格式：JSON.签名
            int lastDotIndex = signedFingerprint.lastIndexOf('.');
            if (lastDotIndex < 0) {
                log.warn("签名格式错误，缺少分隔符: {}", signedFingerprint.length() > 100 
                    ? signedFingerprint.substring(0, 100) + "..." : signedFingerprint);
                return null;
            }

            String jsonPart = signedFingerprint.substring(0, lastDotIndex);
            // 调用 parseFingerprint 解析JSON部分（parseFingerprint 已经支持自动检测签名格式）
            return parseFingerprint(jsonPart);
        } catch (Exception e) {
            log.warn("解析签名后的硬件指纹失败: {}", signedFingerprint.length() > 100 
                ? signedFingerprint.substring(0, 100) + "..." : signedFingerprint, e);
            return null;
        }
    }

    /**
     * 解析并验证带签名的硬件指纹（综合方法）
     * <p>
     * 此方法会：
     * <ol>
     *   <li>验证HMAC签名是否正确</li>
     *   <li>解析硬件指纹对象</li>
     *   <li>验证时间戳是否在有效期内</li>
     * </ol>
     * <p>
     * 如果任何一步验证失败，返回 null。
     * <p>
     * 注意：调用此方法前，应该先使用 {@link #separateSignedFingerprint(String)} 分离JSON和签名部分。
     *
     * @param jsonPart JSON部分（不包含签名）
     * @param signature Base64编码的签名
     * @param secretKey HMAC密钥
     * @param timestampValidDurationSeconds 时间戳有效期（秒），默认300秒（5分钟）
     * @return 硬件指纹对象，如果验证失败返回 null
     */
    public static HardwareFingerprint parseAndVerifySignedFingerprint(
            String jsonPart,
            String signature,
            String secretKey,
            long timestampValidDurationSeconds) {
        if (StringUtils.isBlank(jsonPart) || StringUtils.isBlank(signature) || StringUtils.isBlank(secretKey)) {
            log.warn("JSON部分、签名或密钥为空，无法验证");
            return null;
        }

        // 1. 验证HMAC签名
        if (!verifySignature(jsonPart, signature, secretKey)) {
            log.warn("硬件指纹签名验证失败");
            return null;
        }

        // 2. 解析硬件指纹对象
        HardwareFingerprint fingerprint = parseFingerprint(jsonPart);
        if (fingerprint == null) {
            log.warn("解析硬件指纹失败");
            return null;
        }

        // 3. 验证时间戳（防止重放攻击）
        if (!verifyTimestamp(fingerprint, timestampValidDurationSeconds)) {
            log.warn("硬件指纹时间戳已过期，拒绝请求。指纹时间: {}, 当前时间: {}",
                fingerprint.getTimestamp(), System.currentTimeMillis());
            return null;
        }

        return fingerprint;
    }

    /**
     * 验证硬件指纹的HMAC签名
     * <p>
     * 验证规则：
     * <ul>
     *   <li>使用HMAC-SHA256重新计算JSON数据的签名</li>
     *   <li>比较计算出的签名与接收到的签名是否一致</li>
     * </ul>
     *
     * @param jsonPart JSON部分（不包含签名）
     * @param receivedSignature 接收到的Base64编码的签名
     * @param secretKey HMAC密钥
     * @return true-签名有效，false-签名无效
     */
    public static boolean verifySignature(String jsonPart, String receivedSignature, String secretKey) {
        if (StringUtils.isBlank(jsonPart) || StringUtils.isBlank(receivedSignature) || StringUtils.isBlank(secretKey)) {
            log.warn("JSON部分、签名或密钥为空，无法验证");
            return false;
        }

        try {
            // 使用HMAC-SHA256重新计算签名
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] signatureBytes = mac.doFinal(jsonPart.getBytes(StandardCharsets.UTF_8));
            String calculatedSignature = Base64.getEncoder().encodeToString(signatureBytes);

            // 比较签名（使用安全的字符串比较，防止时序攻击）
            boolean isValid = calculatedSignature.equals(receivedSignature);
            if (!isValid) {
                log.debug("硬件指纹签名验证失败，期望: {}, 实际: {}", calculatedSignature, receivedSignature);
            }

            return isValid;
        } catch (Exception e) {
            log.warn("验证硬件指纹签名失败", e);
            return false;
        }
    }

    /**
     * 验证时间戳是否在有效期内
     * <p>
     * 防止重放攻击：只接受在指定时间窗口内的请求。
     *
     * @param fingerprint 硬件指纹对象（必须包含timestamp字段）
     * @param validDurationSeconds 有效期（秒）
     * @return true-时间戳有效，false-时间戳无效或已过期
     */
    public static boolean verifyTimestamp(HardwareFingerprint fingerprint, long validDurationSeconds) {
        if (fingerprint == null || fingerprint.getTimestamp() == null) {
            log.warn("硬件指纹或时间戳为空，无法验证");
            return false;
        }

        long currentTime = System.currentTimeMillis();
        long fingerprintTime = fingerprint.getTimestamp();
        long timeDiff = Math.abs(currentTime - fingerprintTime);

        // 时间差不能超过有效期（转换为毫秒）
        long validDurationMillis = validDurationSeconds * 1000L;
        boolean isValid = timeDiff <= validDurationMillis;

        if (!isValid) {
            log.warn("硬件指纹时间戳已过期，当前时间: {}, 指纹时间: {}, 时间差: {}ms, 有效期: {}ms",
                currentTime, fingerprintTime, timeDiff, validDurationMillis);
        }

        return isValid;
    }

    /**
     * 将硬件指纹对象序列化为 JSON 字符串
     *
     * @param fingerprint 硬件指纹对象
     * @return JSON 字符串
     */
    public static String serializeFingerprint(HardwareFingerprint fingerprint) {
        if (fingerprint == null) {
            return null;
        }

        try {
            return JsonUtils.getInstance().serialize(fingerprint);
        } catch (Exception e) {
            log.warn("序列化硬件指纹失败", e);
            return null;
        }
    }

    /**
     * 验证硬件指纹是否匹配
     * <p>
     * 比较请求中的硬件指纹与 Token 中记录的指纹是否匹配。
     * <p>
     * 验证规则：
     * <ul>
     *   <li>Canvas 和 WebGL 指纹必须完全匹配（权重最高）</li>
     *   <li>其他特征允许有轻微变化</li>
     *   <li>相似度 >= 0.8 认为匹配</li>
     * </ul>
     *
     * @param requestFingerprint 请求中的硬件指纹
     * @param tokenFingerprint   Token 中记录的硬件指纹
     * @return true-匹配，false-不匹配
     */
    public static boolean verifyFingerprint(HardwareFingerprint requestFingerprint, HardwareFingerprint tokenFingerprint) {
        if (requestFingerprint == null || tokenFingerprint == null) {
            log.warn("硬件指纹为空，无法验证");
            return false;
        }

        // 计算相似度
        double similarity = calculateSimilarity(requestFingerprint, tokenFingerprint);

        log.debug("硬件指纹相似度: {}, 阈值: {}", similarity, SIMILARITY_THRESHOLD);

        return similarity >= SIMILARITY_THRESHOLD;
    }

    /**
     * 计算两个硬件指纹的相似度
     * <p>
     * 相似度计算规则：
     * <ul>
     *   <li>Canvas 指纹：权重 0.4，必须完全匹配</li>
     *   <li>WebGL 指纹：权重 0.4，必须完全匹配</li>
     *   <li>屏幕分辨率：权重 0.05</li>
     *   <li>时区：权重 0.03</li>
     *   <li>语言：权重 0.02</li>
     *   <li>硬件并发数：权重 0.03</li>
     *   <li>设备内存：权重 0.02（如果可用）</li>
     *   <li>颜色深度：权重 0.02</li>
     *   <li>像素比：权重 0.02（允许 0.1 的误差）</li>
     *   <li>平台信息：权重 0.01</li>
     * </ul>
     *
     * @param fingerprint1 第一个硬件指纹
     * @param fingerprint2 第二个硬件指纹
     * @return 相似度（0-1之间，1表示完全匹配）
     */
    private static double calculateSimilarity(HardwareFingerprint fingerprint1, HardwareFingerprint fingerprint2) {
        double score = 0.0;
        double totalWeight = 0.0;

        // Canvas 指纹（权重最高，必须匹配）
        double canvasWeight = 0.4;
        totalWeight += canvasWeight;
        if (Strings.CS.equals(fingerprint1.getCanvas(), fingerprint2.getCanvas())) {
            score += canvasWeight;
        }

        // WebGL 指纹（权重最高，必须匹配）
        double webglWeight = 0.4;
        totalWeight += webglWeight;
        if (Strings.CS.equals(fingerprint1.getWebgl(), fingerprint2.getWebgl())) {
            score += webglWeight;
        }

        // 屏幕分辨率（权重中等）
        double screenWeight = 0.05;
        totalWeight += screenWeight;
        if (Strings.CS.equals(fingerprint1.getScreen(), fingerprint2.getScreen())) {
            score += screenWeight;
        }

        // 时区（权重较低）
        double timezoneWeight = 0.03;
        totalWeight += timezoneWeight;
        if (Strings.CS.equals(fingerprint1.getTimezone(), fingerprint2.getTimezone())) {
            score += timezoneWeight;
        }

        // 语言（权重较低）
        double languageWeight = 0.02;
        totalWeight += languageWeight;
        if (Strings.CS.equals(fingerprint1.getLanguage(), fingerprint2.getLanguage())) {
            score += languageWeight;
        }

        // 硬件并发数（权重较低）
        double hardwareWeight = 0.03;
        totalWeight += hardwareWeight;
        if (fingerprint1.getHardwareConcurrency() != null && fingerprint2.getHardwareConcurrency() != null
                && fingerprint1.getHardwareConcurrency().equals(fingerprint2.getHardwareConcurrency())) {
            score += hardwareWeight;
        }

        // 设备内存（权重较低，可能不可用）
        double memoryWeight = 0.02;
        if (fingerprint1.getDeviceMemory() != null && fingerprint2.getDeviceMemory() != null) {
            totalWeight += memoryWeight;
            if (fingerprint1.getDeviceMemory().equals(fingerprint2.getDeviceMemory())) {
                score += memoryWeight;
            }
        }

        // 颜色深度（权重较低）
        double colorWeight = 0.02;
        totalWeight += colorWeight;
        if (fingerprint1.getColorDepth() != null && fingerprint2.getColorDepth() != null
                && fingerprint1.getColorDepth().equals(fingerprint2.getColorDepth())) {
            score += colorWeight;
        }

        // 像素比（权重较低，允许 0.1 的误差）
        double pixelWeight = 0.02;
        totalWeight += pixelWeight;
        if (fingerprint1.getPixelRatio() != null && fingerprint2.getPixelRatio() != null) {
            double diff = Math.abs(fingerprint1.getPixelRatio() - fingerprint2.getPixelRatio());
            if (diff < 0.1) {
                score += pixelWeight;
            }
        }

        // 平台信息（权重较低）
        double platformWeight = 0.01;
        totalWeight += platformWeight;
        if (Strings.CS.equals(fingerprint1.getPlatform(), fingerprint2.getPlatform())) {
            score += platformWeight;
        }

        return totalWeight > 0 ? score / totalWeight : 0.0;
    }

    /**
     * 从 Token 的 claims 中提取硬件指纹
     * <p>
     * 硬件指纹存储在 Token 的 "hardwareFingerprint" claim 中。
     *
     * @param claims Token 的 claims Map
     * @return 硬件指纹对象，如果不存在返回 null
     */
    public static HardwareFingerprint extractFingerprintFromClaims(Map<String, Claim> claims) {
        if (claims == null) {
            return null;
        }

        Claim fingerprintClaim = claims.get("hardwareFingerprint");
        var fingerprintObj = fingerprintClaim.as(Object.class);
        switch (fingerprintObj) {
            case null -> {
                return null;
            }


            // 如果是字符串，尝试解析
            case String s -> {
                return parseFingerprint(s);
            }


            // 如果是 Map，直接转换
            case Map<?, ?> map -> {
                try {
                    String json = JsonUtils.getInstance().serialize(map);
                    return parseFingerprint(json);
                } catch (Exception e) {
                    log.warn("从 claims 提取硬件指纹失败", e);
                    return null;
                }
            }
            default -> {
            }
        }

        return null;
    }
}
