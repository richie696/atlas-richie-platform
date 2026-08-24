/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api;

import java.time.Instant;

/**
 * Secret 写入后的版本标识。
 *
 * @param value Provider 版本值
 * @param createdAt 创建时间
 */
public record SecretVersion(String value, Instant createdAt) {
}
