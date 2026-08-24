/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.oauth.starter;

import cn.richie696.component.secret.bootstrap.catalog.SecretBindingCatalogLoader;
import cn.richie696.component.secret.bootstrap.SecretBootstrapTestHarness;
import cn.richie696.component.oauth.starter.config.OAuthResourceServerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthResourceServerSecretBindingCatalogTest {

    @Test
    void declaresOpaqueIntrospectionClientSecret() {
        var catalog = new SecretBindingCatalogLoader().load(getClass().getClassLoader());

        assertThat(catalog.propertySourceBinding(
                "platform.oauth.resource-server.introspection-client-secret")).isPresent();
    }

    @Test
    void enabledOverridesAndDisabledPreservesIntrospectionSecret() {
        Map<String, Object> remote = Map.of(
                "platform.oauth.resource-server.introspection-client-secret", "remote-client-secret");
        var enabled = SecretBootstrapTestHarness.apply(getClass().getClassLoader(), Map.of(
                "spring.application.name", "resource-server-test",
                "platform.component.secret.enabled", true,
                "platform.component.secret.strict-mode", true,
                "platform.oauth.resource-server.introspection-client-secret", "local-client-secret"), remote);
        var disabled = SecretBootstrapTestHarness.apply(getClass().getClassLoader(), Map.of(
                "spring.application.name", "resource-server-test",
                "platform.component.secret.enabled", false,
                "platform.oauth.resource-server.introspection-client-secret", "local-client-secret"), remote);

        assertThat(Binder.get(enabled).bind(
                "platform.oauth.resource-server", OAuthResourceServerProperties.class)
                .get().getIntrospectionClientSecret()).isEqualTo("remote-client-secret");
        assertThat(Binder.get(disabled).bind(
                "platform.oauth.resource-server", OAuthResourceServerProperties.class)
                .get().getIntrospectionClientSecret()).isEqualTo("local-client-secret");
    }
}
