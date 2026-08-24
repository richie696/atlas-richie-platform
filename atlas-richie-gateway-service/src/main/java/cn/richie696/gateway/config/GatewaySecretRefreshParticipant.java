/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.gateway.config;

import cn.richie696.component.secret.api.SecretSnapshotChangedEvent;
import cn.richie696.component.secret.bootstrap.refresh.PreparedSecretRefresh;
import cn.richie696.component.secret.bootstrap.refresh.SecretRefreshParticipant;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 原子替换 Gateway 鉴权 Secret；算法和安全模式仍由普通配置管理。
 */
final class GatewaySecretRefreshParticipant implements SecretRefreshParticipant {
    private static final String PREFIX = "platform.gateway.security.authentication";

    private final AuthenticationConfig current;

    GatewaySecretRefreshParticipant(AuthenticationConfig current) {
        this.current = current;
    }

    @Override
    public PreparedSecretRefresh prepare(
            ConfigurableEnvironment environment,
            SecretSnapshotChangedEvent candidate) {
        AuthenticationConfig next = Binder.get(environment)
                .bind(PREFIX, AuthenticationConfig.class)
                .orElseThrow(() -> new IllegalStateException("Gateway authentication configuration is missing"));
        if (StringUtils.isBlank(next.getSecretKey())) {
            throw new IllegalStateException("Gateway authentication Secret must not be blank");
        }
        String previous = current.getSecretKey();
        return new PreparedSecretRefresh() {
            @Override
            public void commit() {
                current.setSecretKey(next.getSecretKey());
            }

            @Override
            public void rollback() {
                current.setSecretKey(previous);
            }
        };
    }
}
