/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap.catalog;

/**
 * Binding 变化后业务组件的生效策略。
 */
public enum SecretRefreshStrategy {
    STATIC,
    REBIND_PROPERTIES,
    RECREATE_CLIENT,
    REFRESH_SERVICE,
    DUAL_VERSION,
    NATIVE
}
