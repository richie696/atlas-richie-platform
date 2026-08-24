/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.mfa.core.config;

import cn.richie696.component.mfa.core.crypto.KeyManagementProvider;
import cn.richie696.component.mfa.core.crypto.MfaKeyManagementBridge;
import cn.richie696.component.secret.api.SecretOperations;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Secret 启用时，以统一外观接管 MFA 的新写与解密路径。 */
@AutoConfiguration(afterName = "cn.richie696.component.secret.starter.SecretRuntimeAutoConfiguration")
@ConditionalOnProperty(
        prefix = "platform.component.secret",
        name = "enabled",
        havingValue = "true")
@ConditionalOnBean(SecretOperations.class)
public class MfaSecretBridgeAutoConfiguration {

    @Bean("mfaKeyManagementBridge")
    @Primary
    @ConditionalOnMissingBean(name = "mfaKeyManagementBridge")
    public KeyManagementProvider mfaKeyManagementBridge(SecretOperations secrets) {
        return new MfaKeyManagementBridge(secrets);
    }
}
