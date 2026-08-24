/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.api.exception.SecretBootstrapException;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.bootstrap.catalog.SecretBindingCatalogLoader;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.mock.env.MockEnvironment;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtlasSecretEnvironmentPostProcessorTest {

    @Test
    void disabledDoesNotDiscoverProvidersOrAddPropertySource() {
        AtomicInteger discoveryCalls = new AtomicInteger();
        SecretProviderDiscovery discovery = discoveryReturning(discoveryCalls, List.of());
        MockEnvironment environment = new MockEnvironment();
        AtlasSecretEnvironmentPostProcessor processor = processor(discovery);

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(discoveryCalls).hasValue(0);
        assertThat(environment.getPropertySources().stream()
                .map(source -> source.getName())
                .noneMatch(name -> name.startsWith("atlas-richie-secret"))).isTrue();
    }

    @Test
    void enabledAddsOnlyCatalogAllowlistedPropertiesAtHighestPriority() {
        AtomicInteger discoveryCalls = new AtomicInteger();
        SecretBootstrapProviderFactory factory = factory(Map.of(
                "platform.component.test.access-key-secret", "remote-secret",
                "platform.component.test.native-key", "must-not-enter-environment",
                "platform.component.ai.chat.openai-4.api-keys[0]", "remote-ai-key",
                "platform.component.ai.chat.openai.extra.api-keys[0]", "must-not-match-two-segments",
                "platform.component.secret.vault.endpoint", "https://untrusted.example",
                "server.port", "1"));
        SecretProviderDiscovery discovery = discoveryReturning(discoveryCalls, List.of(factory));
        MockEnvironment environment = enabledEnvironment();
        environment.setProperty("platform.component.test.access-key-secret", "local-secret");

        processor(discovery).postProcessEnvironment(environment, new SpringApplication());

        assertThat(discoveryCalls).hasValue(1);
        assertThat(environment.getProperty("platform.component.test.access-key-secret"))
                .isEqualTo("remote-secret");
        assertThat(environment.getProperty("platform.component.test.native-key")).isNull();
        assertThat(environment.getProperty("platform.component.ai.chat.openai-4.api-keys[0]"))
                .isEqualTo("remote-ai-key");
        assertThat(environment.getProperty("platform.component.ai.chat.openai.extra.api-keys[0]")).isNull();
        assertThat(environment.getProperty("platform.component.secret.vault.endpoint")).isNull();
        assertThat(environment.getProperty("server.port")).isNull();
        assertThat(environment.getPropertySources().iterator().next().getName())
                .isEqualTo("atlas-richie-secret[test:test-v1]");
    }

    @Test
    void requiredSecretMissingFailsClosed() {
        MockEnvironment environment = enabledEnvironment();
        environment.setProperty("platform.component.test.engine", "remote");
        SecretProviderDiscovery discovery = discoveryReturning(
                new AtomicInteger(),
                List.of(factory(Map.of())));

        assertThatThrownBy(() -> processor(discovery)
                .postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOf(SecretBootstrapException.class)
                .hasMessageContaining("platform.component.test.access-key-secret");
    }

    @Test
    void validationFailureClosesBootstrapClient() {
        MockEnvironment environment = enabledEnvironment();
        environment.setProperty("platform.component.test.engine", "remote");
        AtomicBoolean closed = new AtomicBoolean();
        SecretBootstrapProviderFactory factory = new SecretBootstrapProviderFactory() {
            @Override
            public String providerType() {
                return "test";
            }

            @Override
            public Set<SecretCapability> capabilities() {
                return Set.of(SecretCapability.SECRET_READ);
            }

            @Override
            public SecretBootstrapClient create(
                    BootstrapSecretProperties properties,
                    SecretBootstrapContext context) {
                return new SecretBootstrapClient() {
                    @Override
                    public SecretBootstrapResult load(SecretBootstrapRequest request) {
                        return new SecretBootstrapResult(
                                "test", "test-v1", "source", Instant.EPOCH, Map.of(), "request-1");
                    }

                    @Override
                    public void close() {
                        closed.set(true);
                    }
                };
            }
        };

        assertThatThrownBy(() -> processor(discoveryReturning(
                new AtomicInteger(), List.of(factory)))
                .postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOf(SecretBootstrapException.class);
        assertThat(closed).isTrue();
    }

    @Test
    void nonStrictLocalFallbackRequiresCatalogPermission() {
        MockEnvironment environment = enabledEnvironment();
        environment.setProperty("platform.component.secret.strict-mode", "false");
        environment.setProperty("platform.component.secret.property-source.missing-policy", "local");
        environment.setProperty("platform.component.secret.property-source.local-fallback", "true");
        environment.setProperty("platform.component.test.engine", "remote");
        environment.setProperty("platform.component.test.access-key-secret", "local-dev-secret");

        processor(discoveryReturning(new AtomicInteger(), List.of(factory(Map.of()))))
                .postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("platform.component.test.access-key-secret"))
                .isEqualTo("local-dev-secret");
    }

    @Test
    void processorRunsImmediatelyAfterConfigData() {
        assertThat(processor(discoveryReturning(new AtomicInteger(), List.of())).getOrder())
                .isEqualTo(ConfigDataEnvironmentPostProcessor.ORDER + 1);
    }

    @Test
    void rejectsControlCharactersInActiveProfile() {
        MockEnvironment environment = enabledEnvironment();
        environment.setActiveProfiles("prod\nprofile");

        assertThatThrownBy(() -> processor(discoveryReturning(
                new AtomicInteger(), List.of(factory(Map.of()))))
                .postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("Invalid active Secret profile");
    }

    @Test
    void rejectsOverlongBundleScope() {
        MockEnvironment environment = enabledEnvironment();
        environment.setProperty(
                "platform.component.secret.property-source.paths[0]",
                "a".repeat(129));

        assertThatThrownBy(() -> processor(discoveryReturning(
                new AtomicInteger(), List.of(factory(Map.of()))))
                .postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("Invalid Secret Bundle scope");
    }

    private AtlasSecretEnvironmentPostProcessor processor(SecretProviderDiscovery discovery) {
        return new AtlasSecretEnvironmentPostProcessor(
                new DefaultBootstrapContext(),
                discovery,
                new SecretBindingCatalogLoader());
    }

    private MockEnvironment enabledEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.application.name", "test-service");
        environment.setProperty("platform.component.secret.enabled", "true");
        environment.setProperty("platform.component.secret.strict-mode", "true");
        environment.setProperty("platform.component.secret.property-source.environment", "test");
        return environment;
    }

    private SecretProviderDiscovery discoveryReturning(
            AtomicInteger calls,
            List<SecretBootstrapProviderFactory> factories) {
        return new SecretProviderDiscovery() {
            @Override
            public List<SecretBootstrapProviderFactory> discover(
                    ClassLoader classLoader,
                    BootstrapSecretProperties properties) {
                calls.incrementAndGet();
                return factories;
            }
        };
    }

    private SecretBootstrapProviderFactory factory(Map<String, Object> values) {
        return new SecretBootstrapProviderFactory() {
            @Override
            public String providerType() {
                return "test";
            }

            @Override
            public Set<SecretCapability> capabilities() {
                return Set.of(SecretCapability.SECRET_READ);
            }

            @Override
            public SecretBootstrapClient create(
                    BootstrapSecretProperties properties,
                    SecretBootstrapContext context) {
                return request -> new SecretBootstrapResult(
                        "test",
                        "test-v1",
                        String.join(",", request.logicalPaths()),
                        Instant.EPOCH,
                        values,
                        "request-1");
            }
        };
    }
}
