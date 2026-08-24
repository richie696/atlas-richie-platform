/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api;

/**
 * Secret 快照切换后的通知契约。
 */
@FunctionalInterface
public interface SecretSnapshotListener {

    void onChanged(SecretSnapshotChangedEvent event);
}
