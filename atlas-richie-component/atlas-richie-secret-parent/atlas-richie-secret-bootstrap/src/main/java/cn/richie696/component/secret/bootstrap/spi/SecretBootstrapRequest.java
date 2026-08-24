/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap.spi;

import cn.richie696.component.secret.bootstrap.catalog.SecretBinding;

import java.util.List;

/**
 * Provider 读取 Bundle 所需的规范化请求。
 */
public record SecretBootstrapRequest(
        String application,
        String environment,
        List<String> logicalPaths,
        List<SecretBinding> bindings) {

    public SecretBootstrapRequest {
        logicalPaths = List.copyOf(logicalPaths);
        bindings = List.copyOf(bindings);
    }
}
