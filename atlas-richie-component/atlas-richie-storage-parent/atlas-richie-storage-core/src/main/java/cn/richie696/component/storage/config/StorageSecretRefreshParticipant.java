/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.storage.config;

import cn.richie696.component.secret.api.SecretSnapshotChangedEvent;
import cn.richie696.component.secret.bootstrap.refresh.PreparedSecretRefresh;
import cn.richie696.component.secret.bootstrap.refresh.SecretRefreshParticipant;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 从候选 Environment 绑定完整 Storage 配置并预建所有远程客户端。
 */
final class StorageSecretRefreshParticipant implements SecretRefreshParticipant {
    private final StorageEngineRegistry registry;

    StorageSecretRefreshParticipant(StorageEngineRegistry registry) {
        this.registry = registry;
    }

    @Override
    public PreparedSecretRefresh prepare(
            ConfigurableEnvironment environment,
            SecretSnapshotChangedEvent candidate) {
        StorageProperties properties = Binder.get(environment)
                .bind("platform.component.storage", StorageProperties.class)
                .orElseGet(StorageProperties::new);
        return registry.prepareSecretRefresh(properties);
    }
}
