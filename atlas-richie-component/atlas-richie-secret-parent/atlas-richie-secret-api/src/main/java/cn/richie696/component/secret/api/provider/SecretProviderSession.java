/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api.provider;

import cn.richie696.component.secret.api.SecretBackend;
import cn.richie696.component.secret.api.crypto.KeyWrappingBackend;
import cn.richie696.component.secret.api.crypto.SigningBackend;

import java.util.Optional;

/**
 * Provider 的线程安全运行期会话。
 */
public interface SecretProviderSession extends AutoCloseable {

    SecretProviderDescriptor descriptor();

    Optional<SecretBackend> secretBackend();

    Optional<KeyWrappingBackend> keyWrappingBackend();

    /** Optional provider-managed signing capability. */
    default Optional<SigningBackend> signingBackend() {
        return Optional.empty();
    }

    @Override
    void close();
}
