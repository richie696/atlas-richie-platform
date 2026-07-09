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
package com.richie.component.mfa.core.config;

import com.richie.component.mfa.core.config.properties.MfaKeyManagementProperties;
import com.richie.component.mfa.core.constant.KeyManagementProviderEnum;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Vault 依赖检查器
 * <p>
 * 在应用启动时检查是否配置了 Vault 但缺少必要的依赖
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(MfaKeyManagementProperties.class)
@RequiredArgsConstructor
public class VaultDependencyChecker {

    /**
     * 密钥管理配置属性（用于检查是否启用了 Vault）
     */
    private final MfaKeyManagementProperties keyManagementProperties;

    /**
     * 初始化：检查依赖
     * <p>
     * 在 Spring 容器启动后，检查是否配置了 Vault 但缺少必要的依赖
     */
    @PostConstruct
    public void checkDependencies() {
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

}
