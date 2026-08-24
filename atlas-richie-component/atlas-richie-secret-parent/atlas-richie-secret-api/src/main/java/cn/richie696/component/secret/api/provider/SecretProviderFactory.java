/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api.provider;

/**
 * Provider 运行期工厂 SPI。
 */
public interface SecretProviderFactory {

    String providerType();

    SecretProviderDescriptor descriptor();

    SecretProviderSession open(SecretProviderConfiguration configuration);
}
