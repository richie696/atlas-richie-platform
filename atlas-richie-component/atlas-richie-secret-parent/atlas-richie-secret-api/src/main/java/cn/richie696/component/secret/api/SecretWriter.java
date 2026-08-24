/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api;

/**
 * 可选的 Secret 管理平面写入接口，不进入默认只读装配。
 */
public interface SecretWriter {

    SecretVersion put(SecretReference reference, SecretValue value);

    void deleteVersion(SecretReference reference, SecretVersion version);
}
