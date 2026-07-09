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
package com.richie.component.mfa.core.crypto.provider;

import com.richie.component.cache.GlobalCache;
import com.richie.component.mfa.core.config.properties.MfaLocalCryptoProperties;
import com.richie.component.mfa.core.crypto.KeyManagementProvider;
import com.richie.component.mfa.core.support.MfaTenantSupport;
import com.richie.component.mfa.core.util.MfaKeyUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 本地KMS引擎（仅用于开发/测试环境）
 * <p>
 * 使用AES-256-GCM算法进行加密/解密，模拟真实的KMS行为。
 * <p>
 * 此实现位于 core 模块，供 management 和 validation 模块共同使用
 * <p>
 * <b>安全警告</b>：
 * <ul>
 *   <li>此实现仅用于开发/测试环境，生产环境必须使用Vault或其他KMS服务</li>
 *   <li>密钥存储在配置文件中，安全性较低</li>
 *   <li>建议在开发/测试环境也配置独立的密钥，避免使用默认密钥</li>
 * </ul>
 * <p>
 * <b>配置说明</b>：
 * <pre>{@code
 * platform:
 *   component:
 *     mfa:
 *       security:
 *         key-management:
 *           provider: local
 *           local:
 *             secret-key: "Base64编码的32字节密钥（可选，未配置时使用默认密钥）"
 * }</pre>
 * <p>
 * <b>生成密钥示例</b>：
 * <pre>{@code
 * SecureRandom random = new SecureRandom();
 * byte[] key = new byte[32];
 * random.nextBytes(key);
 * String base64Key = Base64.getEncoder().encodeToString(key);
 * System.out.println("Local KMS Key: " + base64Key);
 * }</pre>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "platform.component.mfa.security.key-management",
    name = "provider",
    havingValue = "local",
    matchIfMissing = true  // 如果未配置provider，默认使用local
)
public class LocalKeyManagementEngine implements KeyManagementProvider {

    private static final String DEFAULT_ALGORITHM = "AES/GCM/NoPadding";
    private static final int DEFAULT_GCM_IV_LENGTH = 12;
    private static final int DEFAULT_GCM_TAG_LENGTH = 16;
    private static final int AES_KEY_LENGTH = 32; // 256位

    /**
     * 默认密钥（仅用于开发/测试，生产环境必须配置独立密钥）
     * <p>
     * 警告：此默认密钥仅用于快速启动和测试，生产环境必须通过配置提供独立密钥
     * <p>
     * 这是一个32字节（256位）的随机密钥，Base64编码后的值
     */
    private static final String DEFAULT_SECRET_KEY_BASE64 =
        "LU52qOTrOtaVVQkzgdx8I3w4H2IL27ijp/ynkkRR+M4="; // 32字节随机密钥的Base64编码

    /**
     * 本地加密配置属性（从 core 模块的配置类读取）
     * <p>
     * 配置前缀：{@code platform.component.mfa.security.key-management.local}
     */
    private final MfaLocalCryptoProperties localCryptoProperties;

    /**
     * 租户支持类（用于判断是否启用租户功能）
     */
    private final MfaTenantSupport tenantSupport;

    /**
     * 本地加密密钥（AES-256，32字节）
     * <p>
     * 从配置中读取或使用默认密钥（仅用于开发/测试）
     * <p>
     * 注意：此密钥仅用于 encrypt/decrypt 方法（向后兼容），storeSecret/retrieveSecret 不使用加密
     */
    private SecretKeySpec secretKey;

    /**
     * 密钥存储 TTL（秒，默认 30 天）
     */
    private static final long SECRET_STORAGE_TTL_SECONDS = 30 * 24 * 3600L;

    /**
     * 初始化加密密钥
     * <p>
     * 从配置中读取本地加密密钥，如果未配置则使用默认密钥（仅用于开发/测试）
     * <p>
     * 密钥格式：Base64编码的32字节（256位）密钥
     *
     * @throws RuntimeException 如果密钥格式错误或初始化失败
     */
    @PostConstruct
    public void init() {
        String secretKeyBase64 = localCryptoProperties.getSecretKey();

        if (StringUtils.isBlank(secretKeyBase64)) {
            log.warn("本地KMS密钥未配置，使用默认密钥（仅用于开发/测试，生产环境必须配置独立密钥）");
            secretKeyBase64 = DEFAULT_SECRET_KEY_BASE64;
        } else {
            log.info("使用配置的本地KMS密钥");
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(secretKeyBase64);
            if (keyBytes.length != AES_KEY_LENGTH) {
                throw new IllegalArgumentException(
                        "本地KMS密钥长度必须为32字节（256位），当前长度: %d".formatted(keyBytes.length));
            }
            secretKey = new SecretKeySpec(keyBytes, "AES");
            log.info("本地KMS引擎初始化成功，使用AES-256-GCM加密算法");
        } catch (IllegalArgumentException e) {
            log.error("本地KMS密钥格式错误", e);
            throw new RuntimeException("本地KMS密钥配置错误: %s".formatted(e.getMessage()), e);
        } catch (Exception e) {
            log.error("本地KMS引擎初始化失败", e);
            throw new RuntimeException("本地KMS引擎初始化失败", e);
        }
    }

    /**
     * 加密数据
     * <p>
     * 使用 AES-256-GCM 算法对明文进行加密
     * <p>
     * 加密流程：
     * <ol>
     *   <li>生成随机IV（初始化向量）</li>
     *   <li>使用 AES-256-GCM 算法加密明文</li>
     *   <li>组合IV和密文：IV + 密文</li>
     *   <li>Base64编码返回</li>
     * </ol>
     *
     * @param plaintext 明文数据（必填）
     * @return 加密后的密文（Base64编码，格式：IV + 密文）
     * @throws RuntimeException 如果加密失败
     */
    @Override
    public String encrypt(String plaintext) {
        if (StringUtils.isBlank(plaintext)) {
            return plaintext;
        }

        try {
            log.debug("使用本地KMS引擎加密数据（仅用于开发/测试环境）");

            AlgorithmParam param = getAlgorithmParam();

            Cipher cipher = Cipher.getInstance(param.algorithm);

            // 生成随机IV
            byte[] iv = generateIV(param.ivLength);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(param.tagLength * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            // 加密数据
            byte[] encryptedData = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 组合IV和密文：IV + 密文
            byte[] combined = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);

            // Base64编码返回
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("本地KMS加密失败", e);
            throw new RuntimeException("本地KMS加密失败: %s".formatted(e.getMessage()), e);
        }
    }

    /**
     * 解密数据
     * <p>
     * 使用 AES-256-GCM 算法对密文进行解密
     * <p>
     * 解密流程：
     * <ol>
     *   <li>Base64解码密文</li>
     *   <li>提取IV和密文（前N字节为IV，后续为密文）</li>
     *   <li>使用 AES-256-GCM 算法解密</li>
     *   <li>返回明文</li>
     * </ol>
     *
     * @param ciphertext 密文（Base64编码，格式：IV + 密文，必填）
     * @return 解密后的明文
     * @throws RuntimeException 如果解密失败或密文格式错误
     */
    @Override
    public String decrypt(String ciphertext) {
        if (StringUtils.isBlank(ciphertext)) {
            return ciphertext;
        }

        try {
            log.debug("使用本地KMS引擎解密数据（仅用于开发/测试环境）");

            AlgorithmParam param = getAlgorithmParam();

            // Base64解码
            byte[] combined = Base64.getDecoder().decode(ciphertext);

            // 检查数据长度
            if (combined.length < param.ivLength()) {
                throw new IllegalArgumentException("密文数据太短，无法提取IV");
            }

            // 提取IV和密文
            byte[] iv = new byte[param.ivLength()];
            byte[] encryptedBytes = new byte[combined.length - param.ivLength()];
            System.arraycopy(combined, 0, iv, 0, param.ivLength());
            System.arraycopy(combined, param.ivLength(), encryptedBytes, 0, encryptedBytes.length);

            // 解密
            Cipher cipher = Cipher.getInstance(param.algorithm());
            GCMParameterSpec gcmSpec = new GCMParameterSpec(param.tagLength() * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("本地KMS解密失败", e);
            throw new RuntimeException("本地KMS解密失败: %s".formatted(e.getMessage()), e);
        }
    }

    private LocalKeyManagementEngine.AlgorithmParam getAlgorithmParam() {
        String algorithm = localCryptoProperties.getAlgorithm();
        int ivLength = localCryptoProperties.getGcmIvLength();
        int tagLength = localCryptoProperties.getGcmTagLength();

        // 使用配置的算法，如果未配置则使用默认值
        if (StringUtils.isBlank(algorithm)) {
            algorithm = DEFAULT_ALGORITHM;
        }
        if (ivLength <= 0) {
            ivLength = DEFAULT_GCM_IV_LENGTH;
        }
        if (tagLength <= 0) {
            tagLength = DEFAULT_GCM_TAG_LENGTH;
        }
        return new AlgorithmParam(algorithm, ivLength, tagLength);
    }

    /**
     * 算法参数（内部Record类）
     * <p>
     * 用于封装加密算法的参数信息
     *
     * @param algorithm 算法名称（例如 "AES/GCM/NoPadding"）
     * @param ivLength  IV长度（字节，例如 12）
     * @param tagLength 认证标签长度（字节，例如 16）
     */
    private record AlgorithmParam(String algorithm, int ivLength, int tagLength) {
    }

    /**
     * 检查KMS服务是否可用
     * <p>
     * 本地模式始终可用（如果初始化成功）
     *
     * @return KMS服务是否可用
     * <ul>
     *   <li>{@code true}：密钥已初始化，服务可用</li>
     *   <li>{@code false}：密钥未初始化，服务不可用</li>
     * </ul>
     */
    @Override
    public boolean isAvailable() {
        // 本地模式始终可用（如果初始化成功）
        return secretKey != null;
    }

    /**
     * 生成随机IV（初始化向量）
     * <p>
     * 使用 SecureRandom 生成指定长度的随机IV
     *
     * @param length IV长度（字节，必填）
     * @return IV字节数组
     */
    private byte[] generateIV(int length) {
        byte[] iv = new byte[length];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
        return iv;
    }

    /**
     * 构建密钥存储路径（用于返回密钥引用）
     * <p>
     * 路径格式：
     * <ul>
     *   <li>有租户：{@code mfa/{tenantId}/{userId}}</li>
     *   <li>无租户：{@code mfa/{userId}}</li>
     * </ul>
     *
     * @param tenantId 租户ID（可选）
     * @param userId   用户ID（必填）
     * @return 密钥存储路径（作为密钥引用返回）
     */
    private String buildSecretPath(String tenantId, String userId) {
        if (tenantId != null && !tenantId.isEmpty()) {
            return "mfa/%s/%s".formatted(tenantId, userId);
        }
        return "mfa/%s".formatted(userId);
    }

    /**
     * 从密钥引用中解析租户ID和用户ID
     * <p>
     * 密钥引用格式：{@code mfa/{tenantId}/{userId}} 或 {@code mfa/{userId}}
     *
     * @param secretReference 密钥引用
     * @return 数组，[0] = tenantId（可能为null），[1] = userId
     */
    private String[] parseSecretReference(String secretReference) {
        if (secretReference == null || !secretReference.startsWith("mfa/")) {
            throw new IllegalArgumentException("无效的密钥引用格式: %s".formatted(secretReference));
        }
        String[] parts = secretReference.substring(4).split("/", 2);
        if (parts.length == 1) {
            // 无租户：mfa/{userId}
            return new String[]{null, parts[0]};
        } else {
            // 有租户：mfa/{tenantId}/{userId}
            return new String[]{parts[0], parts[1]};
        }
    }

    @Override
    public String storeSecret(String tenantId, String userId, String plainSecret) {
        try {
            String secretPath = buildSecretPath(tenantId, userId);
            String cacheKey = MfaKeyUtils.getSecretKeyCacheKey(tenantId, userId, tenantSupport.isTenantEnabled());

            // 存储到 Redis（明文存储，不加密）
            GlobalCache.value().set(cacheKey, plainSecret, SECRET_STORAGE_TTL_SECONDS * 1000L);

            log.info("密钥已存储到 Redis，路径: {}, cacheKey: {}, 密钥长度: {}（仅用于开发/测试）",
                secretPath, cacheKey, plainSecret != null ? plainSecret.length() : 0);
            return secretPath;
        } catch (Exception e) {
            log.error("Redis存储密钥失败，tenantId: {}, userId: {}", tenantId, userId, e);
            throw new RuntimeException("Redis存储密钥失败: %s".formatted(e.getMessage()), e);
        }
    }

    @Override
    public String retrieveSecret(String secretReference) {
        try {
            // 从密钥引用解析租户ID和用户ID
            String[] parts = parseSecretReference(secretReference);
            String tenantId = parts[0];
            String userId = parts[1];

            String cacheKey = MfaKeyUtils.getSecretKeyCacheKey(tenantId, userId, tenantSupport.isTenantEnabled());
            log.info("从 Redis 检索密钥，secretReference: {}, 解析后 tenantId: {}, userId: {}, cacheKey: {}",
                secretReference, tenantId, userId, cacheKey);

            String secret = GlobalCache.value().get(cacheKey, String.class);

            if (secret == null || secret.isEmpty()) {
                log.error("密钥不存在，secretReference: {}, cacheKey: {}", secretReference, cacheKey);
                throw new RuntimeException("密钥不存在，路径: %s, cacheKey: %s".formatted(secretReference, cacheKey));
            }

            log.info("密钥已从 Redis 检索，路径: {}, cacheKey: {}, 密钥长度: {}（仅用于开发/测试）",
                secretReference, cacheKey, secret.length());
            return secret;
        } catch (Exception e) {
            log.error("Redis检索密钥失败，路径: {}", secretReference, e);
            throw new RuntimeException("Redis检索密钥失败: %s".formatted(e.getMessage()), e);
        }
    }

    @Override
    public void deleteSecret(String secretReference) {
        try {
            // 从密钥引用解析租户ID和用户ID
            String[] parts = parseSecretReference(secretReference);
            String tenantId = parts[0];
            String userId = parts[1];

            String cacheKey = MfaKeyUtils.getSecretKeyCacheKey(tenantId, userId, tenantSupport.isTenantEnabled());
            GlobalCache.key().removeCache(cacheKey);

            log.debug("密钥已从 Redis 删除，路径: {}, cacheKey: {}（仅用于开发/测试）", secretReference, cacheKey);
        } catch (Exception e) {
            log.error("Redis删除密钥失败，路径: {}", secretReference, e);
            throw new RuntimeException("Redis删除密钥失败: %s".formatted(e.getMessage()), e);
        }
    }
}
