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

import com.richie.component.mfa.core.config.properties.MfaKeyManagementProperties;
import com.richie.component.mfa.core.config.properties.MfaVaultProperties;
import com.richie.component.mfa.core.crypto.KeyManagementProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultTransitOperations;
import org.springframework.vault.support.VaultResponseSupport;

import java.util.HashMap;
import java.util.Map;

/**
 * Vault KMS引擎
 * <p>
 * 使用HashiCorp Vault的Transit引擎进行密钥加密/解密
 * <p>
 * 此实现使用 Spring Cloud Vault 自动配置的 VaultTemplate，而不是自己创建
 * <p>
 * 此实现位于 core 模块，供 management 和 validation 模块共同使用
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springframework.vault.authentication.ClientAuthentication")
@ConditionalOnBean(VaultTemplate.class)
@ConditionalOnProperty(
    prefix = "platform.component.mfa.security.key-management",
    name = "provider",
    havingValue = "vault"
)
public class VaultKeyManagementEngine implements KeyManagementProvider {

    /**
     * Spring Cloud Vault 自动配置的 VaultTemplate（由 Spring Cloud Vault 自动创建）
     */
    private final VaultTemplate vaultTemplate;

    /**
     * 密钥管理配置属性（用于获取Vault配置）
     */
    private final MfaKeyManagementProperties keyManagementProperties;

    /**
     * Vault Transit 操作接口（用于加密/解密操作）
     */
    private VaultTransitOperations transitOperations;

    /**
     * Vault KV 操作接口（用于存储/检索用户密钥）
     */
    private VaultKeyValueOperations kvOperations;

    /**
     * Transit 密钥名称（在 Vault Transit 引擎中创建的密钥名称）
     */
    private String keyName;

    /**
     * KV 引擎路径（用于存储用户密钥）
     */
    private String kvPath;

    /**
     * 初始化Vault KMS引擎
     * <p>
     * 执行流程：
     * <ol>
     *   <li>从配置读取 Vault 连接信息（Transit路径、密钥名称、KV路径）</li>
     *   <li>使用 Spring Cloud Vault 自动配置的 VaultTemplate</li>
     *   <li>获取 Transit 操作接口</li>
     *   <li>检查密钥是否存在，如果不存在则创建</li>
     * </ol>
     *
     * @throws RuntimeException 如果初始化失败
     */
    @PostConstruct
    public void init() {
        try {
            MfaVaultProperties vaultConfig = keyManagementProperties.getVault();

            // 获取Transit操作接口（用于加密/解密）
            this.transitOperations = vaultTemplate.opsForTransit(vaultConfig.getTransitPath());
            this.keyName = vaultConfig.getKeyName();

            // 获取KV操作接口（用于存储/检索用户密钥，使用 KV v2）
            this.kvPath = vaultConfig.getKvPath();
            this.kvOperations = vaultTemplate.opsForKeyValue(kvPath, VaultKeyValueOperationsSupport.KeyValueBackend.KV_2);

            // 检查密钥是否存在，如果不存在则创建
            ensureKeyExists();

            log.info("Vault KMS引擎初始化成功，使用 Spring Cloud Vault 自动配置的 VaultTemplate");
            log.info("Transit密钥: {}, KV路径: {}", keyName, kvPath);
        } catch (Exception e) {
            log.error("Vault KMS引擎初始化失败", e);
            throw new RuntimeException("Vault KMS引擎初始化失败", e);
        }
    }

    /**
     * 确保Transit密钥存在，如果不存在则创建
     * <p>
     * 在初始化时检查 Vault Transit 引擎中的密钥是否存在，如果不存在则自动创建
     *
     * @throws RuntimeException 如果密钥创建失败
     */
    private void ensureKeyExists() {
        try {
            // 尝试读取密钥信息
            transitOperations.getKey(keyName);
            log.debug("Vault Transit密钥已存在: {}", keyName);
        } catch (Exception e) {
            // 密钥不存在，创建新密钥
            log.info("Vault Transit密钥不存在，正在创建: {}", keyName);
            transitOperations.createKey(keyName);
            log.info("Vault Transit密钥创建成功: {}", keyName);
        }
    }

    /**
     * 轮换 Vault Transit 主密钥版本
     * <p>
     * 调用 Vault Transit 的 rotate 接口，创建新的密钥版本
     * <p>
     * 说明：
     * <ul>
     *   <li>仅创建新的密钥版本，不删除旧版本</li>
     *   <li>历史密文仍可由旧版本密钥解密（受 Vault 自身 min_decryption_version 配置控制）</li>
     *   <li>新写入的数据会自动使用最新版本密钥加密</li>
     * </ul>
     *
     * @throws RuntimeException 如果密钥轮换失败
     */
    public void rotateKey() {
        try {
            transitOperations.rotate(keyName);
            log.info("Vault Transit 密钥轮换成功，密钥名称: {}", keyName);
        } catch (Exception e) {
            log.error("Vault Transit 密钥轮换失败，密钥名称: {}", keyName, e);
            throw new RuntimeException("Vault Transit 密钥轮换失败: %s".formatted(e.getMessage()), e);
        }
    }

    /**
     * 加密数据
     * <p>
     * 使用 Vault Transit 引擎对明文进行加密
     * <p>
     * 返回格式：vault:v1:...（Vault Transit 标准格式）
     *
     * @param plaintext 明文数据（必填）
     * @return 加密后的密文（Vault Transit 格式：vault:v1:...）
     * @throws RuntimeException 如果加密失败
     */
    @Override
    public String encrypt(String plaintext) {
        try {
            // 使用Vault Transit引擎加密
            // Vault Transit的encrypt方法接受String类型的明文，返回加密后的密文（vault:v1:...格式）
            String ciphertext = transitOperations.encrypt(keyName, plaintext);
            log.debug("Vault加密成功，密钥: {}, 密文长度: {}", keyName, ciphertext.length());
            return ciphertext;
        } catch (Exception e) {
            log.error("Vault加密失败，密钥: {}", keyName, e);
            throw new RuntimeException("Vault加密失败: %s".formatted(e.getMessage()), e);
        }
    }

    /**
     * 解密数据
     * <p>
     * 使用 Vault Transit 引擎对密文进行解密
     * <p>
     * 输入格式：vault:v1:...（Vault Transit 标准格式）
     *
     * @param ciphertext 密文（Vault Transit 格式：vault:v1:...，必填）
     * @return 解密后的明文
     * @throws RuntimeException 如果解密失败
     */
    @Override
    public String decrypt(String ciphertext) {
        try {
            // 使用Vault Transit引擎解密
            // Vault Transit的decrypt方法接受密文（vault:v1:...格式），返回明文
            String plaintext = transitOperations.decrypt(keyName, ciphertext);
            log.debug("Vault解密成功，密钥: {}", keyName);
            return plaintext;
        } catch (Exception e) {
            log.error("Vault解密失败，密钥: {}", keyName, e);
            throw new RuntimeException("Vault解密失败: %s".formatted(e.getMessage()), e);
        }
    }

    /**
     * 检查KMS服务是否可用
     * <p>
     * 通过尝试读取密钥信息来检查 Vault 连接是否正常
     *
     * @return KMS服务是否可用
     * <ul>
     *   <li>{@code true}：Vault 连接正常，服务可用</li>
     *   <li>{@code false}：Vault 连接失败，服务不可用</li>
     * </ul>
     */
    @Override
    public boolean isAvailable() {
        try {
            // 尝试读取密钥信息来检查连接
            transitOperations.getKey(keyName);
            return true;
        } catch (Exception e) {
            log.warn("Vault连接检查失败", e);
            return false;
        }
    }

    /**
     * 构建密钥存储路径
     * <p>
     * 路径格式：
     * <ul>
     *   <li>有租户：{@code mfa/{tenantId}/{userId}}</li>
     *   <li>无租户：{@code mfa/{userId}}</li>
     * </ul>
     *
     * @param tenantId 租户ID（可选）
     * @param userId   用户ID（必填）
     * @return 密钥存储路径
     */
    private String buildSecretPath(String tenantId, String userId) {
        if (tenantId != null && !tenantId.isEmpty()) {
            return "mfa/%s/%s".formatted(tenantId, userId);
        }
        return "mfa/%s".formatted(userId);
    }

    @Override
    public String storeSecret(String tenantId, String userId, String plainSecret) {
        try {
            String secretPath = buildSecretPath(tenantId, userId);
            Map<String, Object> data = new HashMap<>();
            data.put("secret", plainSecret);
            kvOperations.put(secretPath, data);
            log.debug("密钥已存储到 Vault KV，路径: {}", secretPath);
            return secretPath;
        } catch (Exception e) {
            log.error("Vault KV 存储密钥失败，tenantId: {}, userId: {}", tenantId, userId, e);
            throw new RuntimeException(buildVaultKvErrorMessage("存储", tenantId, userId, e), e);
        }
    }

    @Override
    public String retrieveSecret(String secretReference) {
        try {
            VaultResponseSupport<Map<String, Object>> response = kvOperations.get(secretReference);
            if (response == null || response.getData() == null) {
                throw new RuntimeException("密钥不存在，路径: %s".formatted(secretReference));
            }
            Object secret = response.getData().get("secret");
            if (secret == null) {
                throw new RuntimeException("密钥数据格式错误，路径: %s".formatted(secretReference));
            }
            log.debug("密钥已从 Vault KV 检索，路径: {}", secretReference);
            return secret.toString();
        } catch (Exception e) {
            log.error("Vault KV 检索密钥失败，路径: {}", secretReference, e);
            throw new RuntimeException(buildVaultKvErrorMessage("检索", null, secretReference, e), e);
        }
    }

    @Override
    public void deleteSecret(String secretReference) {
        try {
            kvOperations.delete(secretReference);
            log.debug("密钥已从 Vault KV 删除，路径: {}", secretReference);
        } catch (Exception e) {
            log.error("Vault KV 删除密钥失败，路径: {}", secretReference, e);
            throw new RuntimeException(buildVaultKvErrorMessage("删除", null, secretReference, e), e);
        }
    }

    /**
     * 构建 Vault KV 相关错误的友好提示。
     * <p>
     * 当 Vault 返回 404 "route entry not found" 时，说明未在配置的路径上启用 KV 引擎，给出启用命令。
     */
    private String buildVaultKvErrorMessage(String action, String tenantId, String userIdOrPath, Exception e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("route entry not found")) {
            return "Vault KV %s密钥失败: 在路径 [%s] 上未启用 KV Secrets Engine（或未启用 KV v2）。"
                .formatted(action, kvPath)
                + " 请在 Vault 中执行: vault secrets enable -path=%s -version=2 kv"
                .formatted(kvPath)
                + " 若 KV 已挂载在其他路径，请配置 platform.component.mfa.security.key-management.vault.kv-path=你的挂载路径";
        }
        return "Vault KV %s密钥失败: %s".formatted(action, msg != null ? msg : e.toString());
    }
}
