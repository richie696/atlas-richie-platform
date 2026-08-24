/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api.provider;

import cn.richie696.component.secret.api.SecretCapability;

import java.util.Set;

/**
 * Provider 的稳定描述信息。
 */
public record SecretProviderDescriptor(
        String providerType,
        String providerId,
        Set<SecretCapability> capabilities) {

    public SecretProviderDescriptor {
        if (providerType == null || providerType.isBlank() || providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerType and providerId must not be blank");
        }
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }
}
