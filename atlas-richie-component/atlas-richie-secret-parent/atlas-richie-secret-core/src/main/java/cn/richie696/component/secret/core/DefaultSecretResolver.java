/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.core;

import cn.richie696.component.secret.api.SecretBackend;
import cn.richie696.component.secret.api.SecretMetadata;
import cn.richie696.component.secret.api.SecretReference;
import cn.richie696.component.secret.api.SecretResolver;
import cn.richie696.component.secret.api.SecretValue;

import java.util.Objects;

/**
 * 将稳定 Resolver API 委托给 Provider SecretBackend。
 */
public final class DefaultSecretResolver implements SecretResolver {
    private final SecretBackend backend;

    public DefaultSecretResolver(SecretBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend must not be null");
    }

    @Override
    public SecretValue resolve(SecretReference reference) {
        return backend.read(Objects.requireNonNull(reference, "reference must not be null"));
    }

    @Override
    public SecretMetadata metadata(SecretReference reference) {
        return backend.metadata(Objects.requireNonNull(reference, "reference must not be null"));
    }
}
