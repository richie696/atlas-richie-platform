/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api;

/**
 * Provider 实现的 Secret Store 读能力。
 */
public interface SecretBackend {

    SecretValue read(SecretReference reference);

    SecretMetadata metadata(SecretReference reference);
}
