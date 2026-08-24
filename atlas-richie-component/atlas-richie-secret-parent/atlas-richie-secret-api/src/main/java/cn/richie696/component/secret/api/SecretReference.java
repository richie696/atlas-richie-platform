/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api;

import java.util.Objects;

/**
 * Provider 无关的 Secret 逻辑引用。
 *
 * <p>业务调用方只声明逻辑名称、版本语义和可选字段。Backend、租户命名空间、
 * Provider 物理路径与路由均由 Core 和 Provider 根据受信任配置解析。</p>
 *
 * @param logicalName Secret 逻辑名称
 * @param version 版本选择器
 * @param field 结构化 Secret 字段，可为空
 */
public record SecretReference(
        String logicalName,
        SecretVersionSelector version,
        String field) {

    public SecretReference {
        logicalName = safeName(logicalName, "logicalName");
        Objects.requireNonNull(version, "version must not be null");
        if (field != null) {
            field = safeName(field, "field");
        }
    }

    public static SecretReference latest(String logicalName) {
        return new SecretReference(logicalName, SecretVersionSelector.latest(), null);
    }

    public static SecretReference latestField(String logicalName, String field) {
        return new SecretReference(logicalName, SecretVersionSelector.latest(), field);
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static String safeName(String value, String fieldName) {
        value = required(value, fieldName);
        if (value.contains("..")
                || value.indexOf('\0') >= 0
                || value.startsWith("/")
                || value.endsWith("/")
                || value.contains("://")) {
            throw new IllegalArgumentException(fieldName + " must be a safe logical name");
        }
        return value;
    }
}
