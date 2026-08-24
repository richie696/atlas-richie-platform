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
        if (properties == null) {
            return true;
        }
        String activeProvider = properties.getActiveProvider();
        if (activeProvider == null || activeProvider.isBlank()) {
            return true;
        }
        String configuredType = properties.getProviders().containsKey(activeProvider)
                ? properties.getProviders().get(activeProvider).getType()
                : activeProvider;
        return configuredType == null || configuredType.isBlank()
                || providerType().equalsIgnoreCase(configuredType);
    }

    Set<SecretCapability> capabilities();

    SecretBootstrapClient create(
            BootstrapSecretProperties properties,
            SecretBootstrapContext context);
}
