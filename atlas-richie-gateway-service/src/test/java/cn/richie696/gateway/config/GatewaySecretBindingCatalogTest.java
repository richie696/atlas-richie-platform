/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.gateway.config;

import cn.richie696.component.secret.bootstrap.catalog.SecretBindingCatalogLoader;
import cn.richie696.component.secret.bootstrap.SecretBootstrapTestHarness;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySecretBindingCatalogTest {

    @Test
    void keepsProviderAndPrivateKeyDetailsOutsideThePropertySourceContract() {
        var catalog = new SecretBindingCatalogLoader().load(getClass().getClassLoader());

        assertThat(catalog.propertySourceBinding(
                "platform.gateway.security.authentication.secret-key")).isPresent();
        assertThat(catalog.propertySourceBinding(
                "platform.gateway.ecc-crypto.gateway-private-key")).isEmpty();
    }

    @Test
    void enabledOverridesAndDisabledPreservesTheGatewaySigningKey() {
        Map<String, Object> remote = Map.of(
                "platform.gateway.security.authentication.secret-key", "remote-gateway-key");
        var enabled = SecretBootstrapTestHarness.apply(getClass().getClassLoader(), Map.of(
                "spring.application.name", "gateway-test",
                "platform.component.secret.enabled", true,
                "platform.component.secret.strict-mode", true,
                "platform.gateway.security.authentication.secret-key", "local-gateway-key"), remote);
        var disabled = SecretBootstrapTestHarness.apply(getClass().getClassLoader(), Map.of(
                "spring.application.name", "gateway-test",
                "platform.component.secret.enabled", false,
                "platform.gateway.security.authentication.secret-key", "local-gateway-key"), remote);

        assertThat(Binder.get(enabled).bind(
                "platform.gateway.security.authentication", AuthenticationConfig.class)
                .get().getSecretKey()).isEqualTo("remote-gateway-key");
        assertThat(Binder.get(disabled).bind(
                "platform.gateway.security.authentication", AuthenticationConfig.class)
                .get().getSecretKey()).isEqualTo("local-gateway-key");
    }
}
