package com.richie.component.mfa.core.crypto;

import com.richie.component.mfa.core.crypto.provider.LocalKeyManagementEngine;

/**
 * KMS提供方接口
 * <p>
 * 用于统一所有KMS提供方实现（Vault、Cloud、HSM、Local）的接口
 * <p>
 * 此接口位于 core 模块，供 management 和 validation 模块共同使用
 * <p>
 * 支持的实现：
 * <ul>
 *   <li>{@link LocalKeyManagementEngine}：本地加密（仅用于开发/测试）</li>
 *   <li>Vault、Cloud、HSM 等实现位于 management 模块（因为它们依赖 management 模块的配置）</li>
 * </ul>
 *
 * @author richie696
 * @since 5.0.0
 */
public interface KeyManagementProvider {

    /**
     * 加密数据
     * <p>
     * 使用配置的 KMS 服务对明文数据进行加密
     *
     * @param plaintext 明文数据（必填）
     * @return 加密后的密文（Base64编码或服务商特定格式）
     * @throws RuntimeException 如果加密失败
     */
    String encrypt(String plaintext);

    /**
     * 解密数据
     * <p>
     * 使用配置的 KMS 服务对密文数据进行解密
     *
     * @param ciphertext 密文（Base64编码或服务商特定格式，必填）
     * @return 解密后的明文
     * @throws RuntimeException 如果解密失败
     */
    String decrypt(String ciphertext);

    /**
     * 检查KMS服务是否可用
     * <p>
     * 用于检查 KMS 服务连接是否正常，是否可以进行加密/解密操作
     *
     * @return KMS服务是否可用
     * <ul>
     *   <li>{@code true}：服务可用，可以进行加密/解密操作</li>
     *   <li>{@code false}：服务不可用，无法进行加密/解密操作</li>
     * </ul>
     */
    boolean isAvailable();

    /**
     * 存储密钥到 KMS
     * <p>
     * 将明文密钥存储到 KMS（如 Vault KV 引擎），返回密钥引用（路径/ID）用于后续检索。
     * <p>
     * <b>安全优势</b>：
     * <ul>
     *   <li>密钥不存储在数据库中，即使数据库泄露也无法获取密钥</li>
     *   <li>密钥存储在专门的密钥管理系统（KMS/HSM）中，符合安全合规要求</li>
     *   <li>数据库只存储密钥引用，无法直接访问密钥内容</li>
     * </ul>
     * <p>
     * <b>实现说明</b>：
     * <ul>
     *   <li>Vault：使用 KV 引擎存储，路径格式如 {@code kv/mfa/{tenantId}/{userId}} 或 {@code kv/mfa/{userId}}</li>
     *   <li>Cloud KMS：使用密钥 ID 或 ARN 作为引用</li>
     *   <li>HSM：使用密钥句柄或密钥 ID 作为引用</li>
     *   <li>Local：使用内存 Map 存储，返回生成的唯一 ID（仅用于开发/测试）</li>
     * </ul>
     *
     * @param tenantId    租户ID（可选，如果未启用租户则为 null）
     * @param userId      用户ID（必填，业务系统User表的主键ID）
     * @param plainSecret 明文密钥（必填，Base32编码的 TOTP 密钥）
     * @return 密钥引用（路径/ID，用于后续检索，例如 {@code kv/mfa/tenant1/user1} 或 {@code key-id-12345}）
     * @throws RuntimeException 如果存储失败
     */
    default String storeSecret(String tenantId, String userId, String plainSecret) {
        // 默认实现：不支持存储，抛出异常提示使用支持存储的实现
        throw new UnsupportedOperationException(
            "当前 KMS 提供方不支持密钥存储功能，请使用支持 KV 存储的实现（如 Vault KV 引擎）"
        );
    }

    /**
     * 从 KMS 检索密钥
     * <p>
     * 根据密钥引用（路径/ID）从 KMS 获取明文密钥。
     * <p>
     * <b>安全注意事项</b>：
     * <ul>
     *   <li>返回的明文密钥仅在内存中使用，使用后应立即清除</li>
     *   <li>不得将明文密钥写入日志或持久化存储</li>
     *   <li>密钥使用完毕后应立即置为null，便于GC回收</li>
     * </ul>
     *
     * @param secretReference 密钥引用（路径/ID，由 {@link #storeSecret} 返回，必填）
     * @return 明文密钥（Base32编码的 TOTP 密钥）
     * @throws RuntimeException 如果检索失败或密钥不存在
     */
    default String retrieveSecret(String secretReference) {
        // 默认实现：不支持检索，抛出异常提示使用支持检索的实现
        throw new UnsupportedOperationException(
            "当前 KMS 提供方不支持密钥检索功能，请使用支持 KV 存储的实现（如 Vault KV 引擎）"
        );
    }

    /**
     * 从 KMS 删除密钥
     * <p>
     * 根据密钥引用（路径/ID）从 KMS 删除密钥（用于解绑场景）。
     * <p>
     * <b>注意</b>：删除操作不可逆，请确保在解绑时调用，避免误删。
     *
     * @param secretReference 密钥引用（路径/ID，由 {@link #storeSecret} 返回，必填）
     * @throws RuntimeException 如果删除失败
     */
    default void deleteSecret(String secretReference) {
        // 默认实现：不支持删除，抛出异常提示使用支持删除的实现
        throw new UnsupportedOperationException(
            "当前 KMS 提供方不支持密钥删除功能，请使用支持 KV 存储的实现（如 Vault KV 引擎）"
        );
    }
}
