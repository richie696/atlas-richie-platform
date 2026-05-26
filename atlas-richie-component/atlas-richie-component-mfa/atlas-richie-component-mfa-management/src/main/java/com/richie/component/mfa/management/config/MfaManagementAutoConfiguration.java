package com.richie.component.mfa.management.config;

import com.richie.component.liquibase.migration.ChangeLogRegistry;
import com.richie.component.mfa.core.config.properties.MfaKeyManagementProperties;
import com.richie.component.mfa.core.constant.KeyManagementProviderEnum;
import com.richie.component.mfa.core.crypto.KeyManagementProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;

/**
 * MFA管理模块自动配置类
 * <p>
 * 部署位置：richie-general-service
 * 职责：自动配置管理相关的Bean，注册Liquibase变更集，自动读取网关租户配置
 *
 * @author richie696
 * @since 5.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "platform.component.mfa",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
@RequiredArgsConstructor
@ComponentScan("com.richie.component.mfa")
@MapperScan("com.richie.component.mfa.management.mapper")
public class MfaManagementAutoConfiguration {

    /**
     * Liquibase变更集注册器（用于注册数据库变更集）
     */
    private final ChangeLogRegistry changeLogRegistry;

    /**
     * 密钥管理配置属性（用于检查是否启用了 Vault）
     */
    private final MfaKeyManagementProperties keyManagementProperties;

    /**
     * 初始化：注册Liquibase变更集并检查依赖
     * <p>
     * 在 Spring 容器启动后，自动注册 MFA 相关的数据库变更集
     * <p>
     * 变更集路径：{@code db/changelog/mfa/db.changelog-master.yaml}
     */
    @PostConstruct
    public void init() {
        // 注册Liquibase变更集
        log.info("Registering MFA Liquibase changelog...");
        changeLogRegistry.add("db/changelog/mfa/db.changelog-master.yaml");

        // 检查是否配置了 Vault 但缺少依赖
        checkVaultDependency();
    }

    /**
     * 检查 Vault 依赖
     * <p>
     * 如果配置了使用 Vault 作为密钥管理引擎，但缺少 Spring Vault 依赖，则输出提示信息
     */
    private void checkVaultDependency() {
        // 检查是否配置了 Vault 作为密钥管理引擎
        if (keyManagementProperties.getProvider() == KeyManagementProviderEnum.VAULT) {
            // 检查是否缺少 Spring Vault 依赖
            if (!isSpringVaultAvailable()) {
                System.err.println("=".repeat(80));
                System.err.println("⚠️  [MFA组件] 检测到配置使用 Vault 作为密钥管理引擎，但缺少必要的依赖！");
                System.err.println("");
                System.err.println("请在项目的 pom.xml 中添加以下依赖：");
                System.err.println("");
                System.err.println("    <dependency>");
                System.err.println("        <groupId>org.springframework.cloud</groupId>");
                System.err.println("        <artifactId>spring-cloud-starter-vault-config</artifactId>");
                System.err.println("    </dependency>");
                System.err.println("");
                System.err.println("配置项：platform.component.mfa.security.key-management.provider=vault");
                System.err.println("");
                System.err.println("注意：使用 Vault 作为密钥管理引擎时，需要在配置文件中设置 Spring Cloud Vault 的连接信息：");
                System.err.println("");
                System.err.println("     spring.cloud.vault.uri=<Vault服务地址>");
                System.err.println("     spring.cloud.vault.token=<Vault认证Token>");
                System.err.println("     spring.cloud.vault.authentication=TOKEN");
                System.err.println("     若使用 https，可配置 spring.cloud.vault.ssl.trust-store 等");
                System.err.println("");
                System.err.println("     如果不需要从 Vault 读取配置，可以禁用 Key-Value Backend：");
                System.err.println("     spring.cloud.vault.kv.enabled=false");
                System.err.println("=".repeat(80));
                log.error("MFA组件配置使用 Vault 作为密钥管理引擎，但缺少 spring-cloud-starter-vault-config 依赖");
            }
        }
    }

    /**
     * 检查 Spring Vault 是否可用
     * <p>
     * 通过尝试加载 Spring Vault 的核心类来判断依赖是否存在
     *
     * @return true-依赖存在，false-依赖不存在
     */
    private boolean isSpringVaultAvailable() {
        try {
            // 尝试加载 Spring Vault 的核心类
            Class.forName("org.springframework.vault.authentication.ClientAuthentication");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * KMS 提供方回退实现 Bean
     * <p>
     * 当没有其他 {@link KeyManagementProvider} 实现可用时（例如配置错误或服务不可用），
     * 使用此回退实现，确保系统可以正常运行（但会记录警告日志）。
     * <p>
     * 注意：此实现不安全（密钥不加密），仅用于开发/测试环境。
     * 生产环境必须提供真正的 KMS 实现（如 Vault、云KMS、HSM等）。
     *
     * @return KMS 提供方回退实现（直接返回明文，不进行加密/解密）
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(KeyManagementProvider.class)
    public KeyManagementProvider localKmsProviderFallback() {
        log.warn("未找到KMS提供方实现，使用本地回退实现（不安全，仅用于开发/测试）");
        return new LocalKmsProviderFallback();
    }

    /**
     * 本地 KMS 提供方回退实现（内部类）。
     * <p>
     * 此实现不进行任何加密/解密操作，直接返回原文。
     * 仅用于开发/测试环境，生产环境必须使用真正的 KMS 实现。
     */
    private static class LocalKmsProviderFallback implements KeyManagementProvider {

        /**
         * 默认构造函数（供 Spring Bean 创建使用）。
         */
        LocalKmsProviderFallback() {
        }

        /**
         * 加密（回退实现：直接返回原文）
         *
         * @param plaintext 明文
         * @return 原文（不加密）
         */
        @Override
        public String encrypt(String plaintext) {
            return plaintext;
        }

        /**
         * 解密（回退实现：直接返回原文）
         *
         * @param ciphertext 密文
         * @return 原文（不解密）
         */
        @Override
        public String decrypt(String ciphertext) {
            return ciphertext;
        }

        /**
         * 检查是否可用（回退实现：始终返回 true）
         *
         * @return 始终返回 true
         */
        @Override
        public boolean isAvailable() {
            return true;
        }
    }

}
