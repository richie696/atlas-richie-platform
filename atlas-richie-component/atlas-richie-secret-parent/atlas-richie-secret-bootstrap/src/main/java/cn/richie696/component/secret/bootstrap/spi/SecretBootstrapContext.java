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
 */
public record SecretBootstrapContext(
        ConfigurableEnvironment environment,
        ClassLoader classLoader) {
}
