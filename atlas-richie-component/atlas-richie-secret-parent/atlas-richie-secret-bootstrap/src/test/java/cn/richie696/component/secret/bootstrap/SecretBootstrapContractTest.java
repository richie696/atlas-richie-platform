/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.catalog.SecretBindingCatalogSet;
import cn.richie696.component.secret.bootstrap.refresh.PreparedSecretRefresh;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretBootstrapContractTest {

    @Test
    void propertiesExposeDefensiveDefaultsAndAllBindingOptions() {
        BootstrapSecretProperties properties = new BootstrapSecretProperties();
        properties.setEnabled(true);
        properties.setStrictMode(false);
        properties.setActiveProvider("vault");
        properties.setPropertySource(null);
        properties.setProviders(Map.of("vault", provider("vault")));
        properties.setRouting(Map.of("secret-read", "vault"));
        properties.setResilience(null);
        properties.setRefresh(null);

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.isStrictMode()).isFalse();
        assertThat(properties.getActiveProvider()).isEqualTo("vault");
        assertThat(properties.getPropertySource()).isNotNull();
        assertThat(properties.getProviders()).containsKey("vault");
        assertThat(properties.getRouting()).containsEntry("secret-read", "vault");
        assertThat(properties.getResilience()).isNotNull();
        assertThat(properties.getRefresh()).isNotNull();

        BootstrapSecretProperties.PropertySource source = new BootstrapSecretProperties.PropertySource();
        source.setApplication("demo");
        source.setEnvironment("test");
        source.setPaths(List.of("common", "secret"));
        source.setMissingPolicy(BootstrapSecretProperties.MissingPolicy.KEEP_LAST_GOOD);
        source.setLocalFallback(true);
        source.setRejectLocalDuplicates(true);
        assertThat(source.getApplication()).isEqualTo("demo");
        assertThat(source.getEnvironment()).isEqualTo("test");
        assertThat(source.getPaths()).containsExactly("common", "secret");
        assertThat(source.getMissingPolicy()).isEqualTo(BootstrapSecretProperties.MissingPolicy.KEEP_LAST_GOOD);
        assertThat(source.isLocalFallback()).isTrue();
        assertThat(source.isRejectLocalDuplicates()).isTrue();

        BootstrapSecretProperties.Resilience resilience = new BootstrapSecretProperties.Resilience();
        resilience.setConnectTimeout(Duration.ofSeconds(1));
        resilience.setReadTimeout(Duration.ofSeconds(2));
        resilience.setMaxAttempts(4);
        assertThat(resilience.getConnectTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(resilience.getReadTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(resilience.getMaxAttempts()).isEqualTo(4);

        BootstrapSecretProperties.Refresh refresh = new BootstrapSecretProperties.Refresh();
        refresh.setEnabled(false);
        refresh.setInitialDelay(Duration.ofSeconds(3));
        refresh.setInterval(Duration.ofSeconds(4));
        refresh.setMaxStaleness(Duration.ofSeconds(5));
        assertThat(refresh.isEnabled()).isFalse();
        assertThat(refresh.getInitialDelay()).isEqualTo(Duration.ofSeconds(3));
        assertThat(refresh.getInterval()).isEqualTo(Duration.ofSeconds(4));
        assertThat(refresh.getMaxStaleness()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void bootstrapStateBuildsDefaultTopologyAndProtectsItsStringRepresentation() {
        SecretBootstrapProviderFactory factory = factory("vault", allCapabilities());
        SecretBootstrapClient client = client();
        SecretBootstrapResult result = new SecretBootstrapResult(
                "vault", "v1", "common", Instant.EPOCH, Map.of(), "req-1");
        BootstrapSecretProperties properties = new BootstrapSecretProperties();

        SecretBootstrapState state = new SecretBootstrapState(properties, factory, client, result);
        assertThat(state.topology()).isNotNull();
        assertThat(state.topology().defaultProviderId()).isEqualTo("vault");
        assertThat(state.toString()).contains("properties=[PROTECTED]").doesNotContain("req-1");

        SecretBootstrapContext context = new SecretBootstrapContext(null, getClass().getClassLoader());
        assertThat(context.providerId()).isNull();
        SecretBootstrapRequest request = new SecretBootstrapRequest("demo", "test", List.of("common"), List.of());
        SecretBootstrapState expanded = new SecretBootstrapState(
                properties, factory, client, result, request, (SecretBindingCatalogSet) null, "common");
        assertThat(expanded.request()).isEqualTo(request);
        assertThat(expanded.propertySourceName()).isEqualTo("common");

        AtomicBoolean committed = new AtomicBoolean();
        PreparedSecretRefresh prepared = new PreparedSecretRefresh() {
            @Override
            public void commit() {
                committed.set(true);
            }

            @Override
            public void rollback() {
                committed.set(false);
            }
        };
        prepared.commit();
        prepared.complete();
        assertThat(committed).isTrue();
    }

    @Test
    void topologyResolvesSingleProviderAndReportsRouteCapabilities() {
        SecretBootstrapProviderFactory factory = factory("vault", allCapabilities());
        SecretBootstrapClient client = client();
        SecretProviderTopology topology = new SecretProviderTopology(
                Map.of("vault", factory), Map.of("vault", client), Map.of(), null);

        assertThat(topology.providerId(SecretProviderTopology.SECRET_READ)).isEqualTo("vault");
        assertThat(topology.providerId(SecretProviderTopology.ENVELOPE_CRYPTO)).isEqualTo("vault");
        assertThat(topology.providerId(SecretProviderTopology.SIGNING)).isEqualTo("vault");
        assertThat(topology.supportsRoute(SecretProviderTopology.SECRET_READ)).isTrue();
        assertThat(topology.supportsRoute("unknown")).isFalse();
        assertThat(topology.factory("vault")).isSameAs(factory);
        assertThat(topology.client("vault")).isSameAs(client);
        assertThatThrownBy(() -> topology.factory("missing")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> topology.client("missing")).isInstanceOf(RuntimeException.class);
        topology.close();
    }

    private BootstrapSecretProperties.Provider provider(String type) {
        BootstrapSecretProperties.Provider provider = new BootstrapSecretProperties.Provider();
        provider.setType(type);
        return provider;
    }

    private Set<SecretCapability> allCapabilities() {
        return Set.of(SecretCapability.SECRET_READ, SecretCapability.SECRET_WRITE,
                SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP,
                SecretCapability.SIGN, SecretCapability.VERIFY);
    }

    private SecretBootstrapProviderFactory factory(String type, Set<SecretCapability> capabilities) {
        return new SecretBootstrapProviderFactory() {
            @Override
            public String providerType() {
                return type;
            }

            @Override
            public Set<SecretCapability> capabilities() {
                return capabilities;
            }

            @Override
            public SecretBootstrapClient create(
                    BootstrapSecretProperties properties, SecretBootstrapContext context) {
                return client();
            }
        };
    }

    private SecretBootstrapClient client() {
        return request -> new SecretBootstrapResult(
                "vault", "v1", "common", Instant.EPOCH, Map.of(), "request");
    }
}
