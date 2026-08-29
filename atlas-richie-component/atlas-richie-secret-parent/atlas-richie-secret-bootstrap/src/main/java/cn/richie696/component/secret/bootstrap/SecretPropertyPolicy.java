/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap;

import java.util.Set;

/**
 * Single security policy for properties that may never be supplied by a Secret Provider.
 *
 * <p>The same policy is applied while loading catalogs, bootstrapping the initial
 * PropertySource and publishing a refreshed snapshot. Keeping it here prevents
 * startup and runtime refresh from enforcing different trust boundaries.</p>
 */
public final class SecretPropertyPolicy {
    private static final Set<String> FORBIDDEN_PROPERTIES = Set.of(
            "server.port",
            "spring.datasource.url",
            "platform.component.secret.enabled",
            "platform.component.oauth.enabled",
            "platform.gateway.authentication.mode",
            "management.endpoints.web.exposure.include");

    private SecretPropertyPolicy() {
    }

    public static boolean isForbidden(String property) {
        return property == null
                || property.startsWith(BootstrapSecretProperties.PREFIX + ".")
                || FORBIDDEN_PROPERTIES.contains(property);
    }
}
