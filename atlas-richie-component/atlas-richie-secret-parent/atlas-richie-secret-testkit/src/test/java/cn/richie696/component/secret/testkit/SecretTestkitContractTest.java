/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.testkit;

import cn.richie696.component.secret.api.SecretReference;
import cn.richie696.component.secret.api.SecretValue;
import cn.richie696.component.secret.api.exception.SecretException;
import cn.richie696.component.secret.bootstrap.SecretProviderDiscovery;
import cn.richie696.component.secret.bootstrap.SecretBootstrapTestSupport;
import cn.richie696.component.secret.bootstrap.catalog.SecretBindingCatalogLoader;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretTestkitContractTest {

    @Test
    void mapBackendStoresMetadataAndDestroysValuesOnClose() {
        MapSecretBackend backend = new MapSecretBackend();
        assertThatThrownBy(() -> backend.put("", new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> backend.put("token", null))
                .isInstanceOf(IllegalArgumentException.class);

        backend.put("token", new byte[]{1, 2, 3});
        SecretReference reference = SecretReference.latest("token");
        try (SecretValue value = backend.read(reference)) {
            assertThat(value.copyBytes()).containsExactly(1, 2, 3);
            assertThat(value.size()).isEqualTo(3);
            assertThat(value.destroyed()).isFalse();
        }
        assertThat(backend.metadata(reference).version()).isEqualTo("test-v1");
        assertThat(backend.metadata(reference).attributes()).containsEntry("backend", "memory");
        assertThatThrownBy(() -> backend.read(SecretReference.latest("missing")))
                .isInstanceOf(SecretException.class)
                .hasMessageContaining("not found");
        assertThatThrownBy(() -> backend.metadata(SecretReference.latest("missing")))
                .isInstanceOf(SecretException.class);
        backend.close();
        assertThatThrownBy(() -> backend.read(reference)).isInstanceOf(SecretException.class);
    }

    @Test
    void testSupportBuildsControlledBootstrapContext() {
        SecretBootstrapTestSupport.postProcessor(
                new DefaultBootstrapContext(),
                new SecretProviderDiscovery(),
                new SecretBindingCatalogLoader());
        SecretBootstrapContext context = new SecretBootstrapContext(
                new MockEnvironment(), getClass().getClassLoader());
        assertThat(context.environment()).isNotNull();
        assertThat(context.classLoader()).isNotNull();
    }

    @Test
    void harnessAppliesCatalogAllowlistedRemoteValue() {
        var environment = SecretBootstrapTestHarness.apply(
                getClass().getClassLoader(),
                Map.of(
                        "spring.application.name", "test-service",
                        "platform.component.secret.enabled", true,
                        "platform.component.secret.strict-mode", true,
                        "platform.component.secret.property-source.environment", "test"),
                Map.of("platform.component.test.access-key-secret", "remote-value"));
        assertThat(environment.getProperty("platform.component.test.access-key-secret"))
                .isEqualTo("remote-value");
    }

    @Test
    void staticFactoryExposesProviderContract() {
        StaticSecretBootstrapProviderFactory factory =
                new StaticSecretBootstrapProviderFactory("memory", Map.of("token", "value"));
        assertThat(factory.providerType()).isEqualTo("memory");
        assertThat(factory.capabilities()).containsExactlyInAnyOrder(
                cn.richie696.component.secret.api.SecretCapability.SECRET_READ);
        var request = new cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest(
                "app", "memory", List.of("common"), List.of());
        var result = factory.create(
                new cn.richie696.component.secret.bootstrap.BootstrapSecretProperties(), context())
                .load(request);
        assertThat(result.values()).containsEntry("token", "value");
    }

    private SecretBootstrapContext context() {
        return new SecretBootstrapContext(new MockEnvironment(), getClass().getClassLoader());
    }
}
