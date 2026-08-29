/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.ai.config;

import cn.richie696.component.secret.bootstrap.catalog.SecretBindingCatalogLoader;
import cn.richie696.component.secret.testkit.SecretBootstrapTestHarness;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiSecretBindingCatalogTest {

    @Test
    void dynamicModelNamesRemainAConstrainedAllowlist() {
        var catalog = new SecretBindingCatalogLoader().load(getClass().getClassLoader());

        assertThat(catalog.propertySourceBinding(
                "platform.component.ai.chat.product-search.api-keys[0]")).isPresent();
        assertThat(catalog.propertySourceBinding(
                "platform.component.ai.voice-chat.realtime.secret-key")).isPresent();
        assertThat(catalog.propertySourceBinding(
                "platform.component.ai.chat.product.extra.api-keys[0]")).isEmpty();
    }

    @Test
    void enabledOverridesAndDisabledPreservesDynamicApiKeys() {
        Map<String, Object> remote = Map.of(
                "platform.component.ai.chat.product-search.api-keys[0]", "remote-ai-key");
        var enabled = SecretBootstrapTestHarness.apply(getClass().getClassLoader(), Map.of(
                "spring.application.name", "ai-test",
                "platform.component.secret.enabled", true,
                "platform.component.secret.strict-mode", true,
                "platform.component.ai.chat.product-search.api-keys[0]", "local-ai-key"), remote);
        var disabled = SecretBootstrapTestHarness.apply(getClass().getClassLoader(), Map.of(
                "spring.application.name", "ai-test",
                "platform.component.secret.enabled", false,
                "platform.component.ai.chat.product-search.api-keys[0]", "local-ai-key"), remote);

        assertThat(Binder.get(enabled).bind("platform.component.ai", AiModelProperties.class)
                .get().getChat().get("product-search").getApiKeys()).containsExactly("remote-ai-key");
        assertThat(Binder.get(disabled).bind("platform.component.ai", AiModelProperties.class)
                .get().getChat().get("product-search").getApiKeys()).containsExactly("local-ai-key");
    }
}
