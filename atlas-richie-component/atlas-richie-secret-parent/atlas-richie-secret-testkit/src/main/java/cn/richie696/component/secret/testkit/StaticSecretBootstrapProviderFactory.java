/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.testkit;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapResult;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * 启动期测试使用的静态 Provider，不执行网络或线程操作。
 */
public final class StaticSecretBootstrapProviderFactory implements SecretBootstrapProviderFactory {
    private final String providerType;
    private final Map<String, Object> values;

    public StaticSecretBootstrapProviderFactory(String providerType, Map<String, Object> values) {
        this.providerType = providerType;
        this.values = Map.copyOf(values);
    }

    @Override
    public String providerType() {
        return providerType;
    }

    @Override
    public Set<SecretCapability> capabilities() {
        return Set.of(SecretCapability.SECRET_READ);
    }

    @Override
    public SecretBootstrapClient create(
            BootstrapSecretProperties properties,
            SecretBootstrapContext context) {
        return request -> new SecretBootstrapResult(
                providerType,
                "test-v1",
                String.join(",", request.logicalPaths()),
                Instant.EPOCH,
                values,
                "test-request");
    }
}
