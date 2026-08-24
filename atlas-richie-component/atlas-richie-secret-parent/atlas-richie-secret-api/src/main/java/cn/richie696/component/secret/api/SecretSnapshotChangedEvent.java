/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api;

import java.time.Instant;

/**
 * 完整 Secret 快照完成原子切换后的脱敏事件。
 *
 * <p>事件只携带 Provider、版本与时间，不携带 Secret 值、属性数量或长度。</p>
 */
public record SecretSnapshotChangedEvent(
        String previousProviderId,
        String previousVersion,
        String currentProviderId,
        String currentVersion,
        Instant changedAt) {

    public SecretSnapshotChangedEvent {
        if (currentProviderId == null || currentProviderId.isBlank()
                || currentVersion == null || currentVersion.isBlank()) {
            throw new IllegalArgumentException("current provider and version must not be blank");
        }
        changedAt = changedAt == null ? Instant.now() : changedAt;
    }
}
