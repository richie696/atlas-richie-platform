/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap.catalog;

/**
 * 单个敏感配置属性的静态契约。
 */
public record SecretBinding(
        String property,
        String logicalName,
        SecretKind kind,
        SecretExposure exposure,
        RequiredWhen requiredWhen,
        SecretRefreshStrategy refresh,
        String owner,
        Boolean allowLocalWhenDisabled,
        Boolean allowLocalWhenEnabled,
        Integer maxLength,
        Boolean shared) {
}
