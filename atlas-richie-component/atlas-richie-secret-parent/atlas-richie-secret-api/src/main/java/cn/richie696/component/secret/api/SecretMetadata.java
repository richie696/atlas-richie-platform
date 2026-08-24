/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api;

import java.time.Instant;
import java.util.Map;

/**
 * 不包含 Secret 值的版本与租约元数据。
 *
 * @param version 版本标识
 * @param createdAt 创建时间
 * @param expiresAt 过期时间，可为空
 * @param attributes 非敏感属性
 */
public record SecretMetadata(
        String version,
        Instant createdAt,
        Instant expiresAt,
        Map<String, String> attributes) {

    public SecretMetadata {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
