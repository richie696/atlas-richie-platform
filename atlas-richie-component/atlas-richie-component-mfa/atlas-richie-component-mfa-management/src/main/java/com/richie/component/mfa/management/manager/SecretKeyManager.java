package com.richie.component.mfa.management.manager;

import com.richie.component.mfa.core.crypto.KeyManagementProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 密钥管理器
 * <p>
 * 职责：生成和加密密钥
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecretKeyManager {

    private static final int SECRET_KEY_LENGTH = 20; // 160位密钥

    /**
     * KMS提供方（根据配置自动注入对应的实现）
     * <p>
     * - 如果配置为 vault，则注入 VaultKmsEngine
     * - 如果配置为 kms，则注入 CloudKmsEngine
     * - 如果配置为 hsm，则注入 HsmKmsEngine
     * - 如果配置为 local（或未配置），则注入 LocalKmsEngine
     */
    private final KeyManagementProvider kmsProvider;

    /**
     * 生成密钥
     * <p>
     * 使用 SecureRandom 生成随机密钥，长度为 160 位（20字节）
     * <p>
     * 使用 Base32 编码（符合 TOTP 标准 RFC 6238），Base32 编码的密钥只包含 A-Z 和 2-7 字符，
     * 不包含特殊字符（如 Base64 的 /、+、=），更适合在 URL 中使用。
     * <p>
     * <b>重要</b>：根据 otpauth URI 规范，Base32 编码的密钥不应该包含填充字符 `=`，
     * 即使密钥长度不能被 8 整除也不应该添加填充。因此需要去掉 Base32 编码后的填充字符。
     *
     * @return Base32编码的密钥（160位随机密钥，不包含填充字符）
     */
    public String generateSecretKey() {
        SecureRandom random = new SecureRandom();
        byte[] key = new byte[SECRET_KEY_LENGTH];
        random.nextBytes(key);
        // 使用 Base32 编码（符合 TOTP 标准）
        Base32 base32 = new Base32();
        String encoded = base32.encodeToString(key);
        // 去掉 Base32 编码的填充字符 `=`（otpauth URI 规范要求不包含填充）
        return encoded.replace("=", "");
    }

    /**
     * 加密密钥
     *
     * <p><b>KMS服务说明</b>：
     * <ul>
     *   <li><b>KMS（Key Management Service）</b>：密钥管理服务，用于安全地创建、存储和管理加密密钥</li>
     *   <li><b>HSM（Hardware Security Module）</b>：硬件安全模块，提供硬件级别的密钥存储和加密运算</li>
     * </ul>
     *
     * <p><b>为什么需要KMS/HSM</b>：
     * <ul>
     *   <li><b>安全合规</b>：符合NIST 800-63B、FIPS 140-2等安全标准要求</li>
     *   <li><b>密钥隔离</b>：主密钥（DEK）存储在KMS/HSM中，应用无法直接访问</li>
     *   <li><b>密钥轮换</b>：支持密钥自动轮换，提高安全性</li>
     *   <li><b>审计追踪</b>：所有密钥操作都有审计日志</li>
     * </ul>
     *
     * <p><b>加密流程</b>：
     * <ol>
     *   <li>从KMS获取数据加密密钥（DEK - Data Encryption Key）</li>
     *   <li>使用DEK通过AES-256-GCM算法加密用户密钥（明文）</li>
     *   <li>将加密后的密钥存储到数据库</li>
     * </ol>
     *
     * <p><b>常见KMS服务</b>：
     * <ul>
     *   <li>云服务商：AWS KMS、阿里云KMS、腾讯云KMS、华为云KMS等</li>
     *   <li>开源方案：HashiCorp Vault、etcd、Consul等</li>
     *   <li>硬件方案：HSM设备（如Thales、SafeNet等）</li>
     * </ul>
     *
     * <p><b>ETCD作为KMS存储后端</b>：
     * <ul>
     *   <li>ETCD本身不是专门的KMS，但可以作为密钥存储后端</li>
     *   <li>主密钥（DEK）存储在ETCD中，使用ETCD的访问控制保护</li>
     *   <li>应用层使用DEK通过AES-256-GCM加密用户密钥</li>
     *   <li>必须启用ETCD的认证和TLS加密</li>
     *   <li>详细方案参见：{@code docs-v2/使用ETCD做软件密码管理方案.md}</li>
     * </ul>
     *
     * <p><b>当前实现</b>：简化实现，直接返回明文（仅用于开发/测试环境）
     * <p><b>生产环境</b>：必须集成KMS/HSM服务，确保密钥安全存储
     *
     * @param plainSecret 明文密钥
     * @return 加密后的密钥（当前简化实现直接返回明文）
     */
    public String encryptSecretKey(String plainSecret) {
        // 检查KMS提供方是否可用
        if (!kmsProvider.isAvailable()) {
            log.warn("KMS提供方不可用，使用本地加密（不安全，仅用于开发）");
            return plainSecret;
        }

        try {
            return kmsProvider.encrypt(plainSecret);
        } catch (Exception e) {
            log.error("KMS加密失败，回退到本地模式（不安全）", e);
            // 加密失败时，为了不阻塞业务流程，返回原文（但会记录错误日志）
            // 生产环境应该配置告警，及时发现此类问题
            return plainSecret;
        }
    }

    /**
     * 解密密钥
     *
     * <p><b>解密流程</b>：
     * <ol>
     *   <li>从数据库读取加密后的密钥</li>
     *   <li>从KMS获取数据加密密钥（DEK）</li>
     *   <li>使用DEK通过AES-256-GCM算法解密</li>
     *   <li>返回明文密钥（仅在内存中使用，使用后立即清除）</li>
     * </ol>
     *
     * <p><b>安全注意事项</b>：
     * <ul>
     *   <li>解密后的明文密钥仅在内存中存在，使用后立即清除</li>
     *   <li>不得将明文密钥写入日志或持久化存储</li>
     *   <li>密钥使用完毕后应立即置为null，便于GC回收</li>
     * </ul>
     *
     * <p><b>当前实现</b>：简化实现，直接返回（仅用于开发/测试环境）
     * <p><b>生产环境</b>：必须集成KMS/HSM服务，确保密钥安全解密
     *
     * @param encryptedSecret 加密后的密钥
     * @return 明文密钥（当前简化实现直接返回）
     */
    public String decryptSecretKey(String encryptedSecret) {
        // 检查KMS提供方是否可用
        if (!kmsProvider.isAvailable()) {
            log.warn("KMS提供方不可用，使用本地解密（不安全，仅用于开发）");
            return encryptedSecret;
        }

        try {
            return kmsProvider.decrypt(encryptedSecret);
        } catch (Exception e) {
            log.error("KMS解密失败", e);
            // 解密失败是严重错误，必须抛出异常，不能返回错误数据
            throw new RuntimeException("KMS解密失败: %s".formatted(e.getMessage()), e);
        }
    }

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
     *
     * @param tenantId    租户ID（可选，如果未启用租户则为 null）
     * @param userId      用户ID（必填，业务系统User表的主键ID）
     * @param plainSecret 明文密钥（必填，Base32编码的 TOTP 密钥）
     * @return 密钥引用（路径/ID，用于后续检索，例如 {@code kv/mfa/tenant1/user1} 或 {@code key-id-12345}）
     * @throws RuntimeException 如果存储失败
     */
    public String storeSecret(String tenantId, String userId, String plainSecret) {
        if (!kmsProvider.isAvailable()) {
            log.warn("KMS提供方不可用，无法存储密钥（不安全，仅用于开发）");
            throw new RuntimeException("KMS提供方不可用，无法存储密钥");
        }

        try {
            return kmsProvider.storeSecret(tenantId, userId, plainSecret);
        } catch (Exception e) {
            log.error("KMS存储密钥失败，tenantId: {}, userId: {}", tenantId, userId, e);
            throw new RuntimeException("KMS存储密钥失败: %s".formatted(e.getMessage()), e);
        }
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
    public String retrieveSecret(String secretReference) {
        if (!kmsProvider.isAvailable()) {
            log.warn("KMS提供方不可用，无法检索密钥（不安全，仅用于开发）");
            throw new RuntimeException("KMS提供方不可用，无法检索密钥");
        }

        try {
            return kmsProvider.retrieveSecret(secretReference);
        } catch (Exception e) {
            log.error("KMS检索密钥失败，secretReference: {}", secretReference, e);
            throw new RuntimeException("KMS检索密钥失败: %s".formatted(e.getMessage()), e);
        }
    }

    /**
     * 从 KMS 检索密钥（重载方法，直接使用 tenantId 和 userId）
     * <p>
     * 根据 tenantId 和 userId 从 KMS 获取明文密钥。
     * <p>
     * <b>安全注意事项</b>：
     * <ul>
     *   <li>返回的明文密钥仅在内存中使用，使用后应立即清除</li>
     *   <li>不得将明文密钥写入日志或持久化存储</li>
     *   <li>密钥使用完毕后应立即置为null，便于GC回收</li>
     * </ul>
     *
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param userId   用户ID（必填，业务系统User表的主键ID）
     * @return 明文密钥（Base32编码的 TOTP 密钥）
     * @throws RuntimeException 如果检索失败或密钥不存在
     */
    public String retrieveSecret(String tenantId, String userId) {
        // 构建密钥引用路径
        String secretReference = buildSecretReference(tenantId, userId);
        return kmsProvider.retrieveSecret(secretReference);
    }

    /**
     * 从 KMS 删除密钥（重载方法，直接使用 tenantId 和 userId）
     * <p>
     * 根据 tenantId 和 userId 从 KMS 删除密钥（用于解绑场景）。
     * <p>
     * <b>注意</b>：删除操作不可逆，请确保在解绑时调用，避免误删。
     *
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param userId   用户ID（必填，业务系统User表的主键ID）
     * @throws RuntimeException 如果删除失败
     */
    public void deleteSecret(String tenantId, String userId) {
        // 构建密钥引用路径
        String secretReference = buildSecretReference(tenantId, userId);
        kmsProvider.deleteSecret(secretReference);
    }

    /**
     * 构建密钥引用路径
     * <p>
     * 路径格式：
     * <ul>
     *   <li>有租户：{@code mfa/{tenantId}/{userId}}</li>
     *   <li>无租户：{@code mfa/{userId}}</li>
     * </ul>
     * <p>
     * <b>注意</b>：此方法只检查 tenantId 是否为空，不检查租户功能是否启用。
     * 因为密钥存储时使用的是传入的 tenantId（可能为 null），检索时也应使用相同的逻辑。
     *
     * @param tenantId 租户ID（可选）
     * @param userId   用户ID（必填）
     * @return 密钥引用路径
     */
    private String buildSecretReference(String tenantId, String userId) {
        // 只检查 tenantId 是否为空，不检查租户功能是否启用
        // 因为密钥存储时使用的是传入的 tenantId（可能为 null），检索时也应使用相同的逻辑
        if (tenantId != null && !tenantId.isEmpty()) {
            return "mfa/%s/%s".formatted(tenantId, userId);
        }
        return "mfa/%s".formatted(userId);
    }
}
