/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.oauth.core.config;

import cn.richie696.component.secret.bootstrap.catalog.SecretBindingCatalogLoader;
import cn.richie696.component.secret.testkit.SecretBootstrapTestHarness;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthSecretBindingCatalogTest {

    @Test
    void declaresTokenSigningSecretWithoutProviderDetails() {
        var catalog = new SecretBindingCatalogLoader().load(getClass().getClassLoader());

        var binding = catalog.propertySourceBinding("platform.component.oauth.token-secret");
        assertThat(binding).isPresent();
        assertThat(binding.orElseThrow().logicalName()).isEqualTo("oauth.token-secret");
    }

    @Test
    void enabledOverridesAndDisabledPreservesTokenSecret() {
        Map<String, Object> remote = Map.of(
                "platform.component.oauth.token-secret", "remote-oauth-secret");
        var enabled = SecretBootstrapTestHarness.apply(getClass().getClassLoader(), Map.of(
                "spring.application.name", "oauth-test",
                "platform.component.secret.enabled", true,
                "platform.component.secret.strict-mode", true,
                "platform.component.oauth.enabled", true,
                "platform.component.oauth.token-secret", "local-oauth-secret"), remote);
        var disabled = SecretBootstrapTestHarness.apply(getClass().getClassLoader(), Map.of(
                "spring.application.name", "oauth-test",
                "platform.component.secret.enabled", false,
                "platform.component.oauth.enabled", true,
                "platform.component.oauth.token-secret", "local-oauth-secret"), remote);

        assertThat(Binder.get(enabled).bind("platform.component.oauth", OAuth2Properties.class)
                .get().getTokenSecret()).isEqualTo("remote-oauth-secret");
        assertThat(Binder.get(disabled).bind("platform.component.oauth", OAuth2Properties.class)
                .get().getTokenSecret()).isEqualTo("local-oauth-secret");
    }
}
