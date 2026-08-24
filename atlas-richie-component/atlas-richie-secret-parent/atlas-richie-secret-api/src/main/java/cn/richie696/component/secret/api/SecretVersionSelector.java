/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api;

import java.util.Objects;

/**
 * Secret 版本选择器，禁止以未经类型约束的字符串表达版本语义。
 *
 * @param type 选择器类型
 * @param value 固定版本、Stage 或 Alias；{@link Type#LATEST} 时为空
 */
public record SecretVersionSelector(Type type, String value) {

    public SecretVersionSelector {
        Objects.requireNonNull(type, "type must not be null");
        if (type != Type.LATEST && (value == null || value.isBlank())) {
            throw new IllegalArgumentException("version selector value must not be blank");
        }
        if (type == Type.LATEST) {
            value = null;
        }
    }

    public static SecretVersionSelector latest() {
        return new SecretVersionSelector(Type.LATEST, null);
    }

    public static SecretVersionSelector version(String version) {
        return new SecretVersionSelector(Type.VERSION, version);
    }

    public static SecretVersionSelector stage(String stage) {
        return new SecretVersionSelector(Type.STAGE, stage);
    }

    public static SecretVersionSelector alias(String alias) {
        return new SecretVersionSelector(Type.ALIAS, alias);
    }

    public enum Type {
        LATEST,
        VERSION,
        STAGE,
        ALIAS
    }
}
