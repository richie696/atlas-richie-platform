/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap.spi;

import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 启动期 Provider 可读取的受控上下文。
 *
 * @param environment ConfigData 加载完成后的 Environment
 * @param classLoader 应用 ClassLoader
 * @param providerId 当前命名 Provider 实例 ID；单 Provider 时等于类型名
 * @param providerType Provider SPI 类型
 * @param configurationPrefix 当前实例的配置前缀
 */
public record SecretBootstrapContext(
        ConfigurableEnvironment environment,
        ClassLoader classLoader,
        String providerId,
        String providerType,
        String configurationPrefix) {

    public SecretBootstrapContext(
            ConfigurableEnvironment environment,
            ClassLoader classLoader) {
        this(environment, classLoader, null, null, null);
    }
}
