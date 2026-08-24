/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap.catalog;

import java.util.List;

/**
 * 单个组件 JAR 提供的 Binding Catalog。
 */
public record SecretBindingCatalog(
        String schemaVersion,
        String component,
        List<SecretBinding> bindings) {

    public SecretBindingCatalog {
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
    }
}
