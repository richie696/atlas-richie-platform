/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap.spi;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;

import java.util.Set;

/**
 * 在 Spring Bean 创建前可用的 Provider 工厂 SPI。
 */
public interface SecretBootstrapProviderFactory {

    String providerType();

    default boolean supports(BootstrapSecretProperties properties) {
        return true;
    }

    Set<SecretCapability> capabilities();

    SecretBootstrapClient create(
            BootstrapSecretProperties properties,
            SecretBootstrapContext context);
}
