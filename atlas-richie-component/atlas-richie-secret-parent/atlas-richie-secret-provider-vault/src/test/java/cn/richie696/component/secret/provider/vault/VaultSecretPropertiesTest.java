/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.vault;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VaultSecretPropertiesTest {

    @Test
    void keepsNestedConfigurationNonNullAndDefensivelyCopiesSecrets() {
        VaultSecretProperties properties = new VaultSecretProperties();
        properties.setEndpoint(URI.create("https://vault.internal"));
        properties.setNamespace("team-a");
        properties.setAuthentication(null);
        properties.setKv(null);
        properties.setTransit(null);
        properties.setTls(null);

        VaultSecretProperties.SecretMapping mapping = new VaultSecretProperties.SecretMapping();
        mapping.setPath("orders");
        mapping.setField("password");
        Map<String, VaultSecretProperties.SecretMapping> mappings = Map.of("orders", mapping);
        properties.setSecrets(mappings);

        assertThat(properties.getEndpoint()).isEqualTo(URI.create("https://vault.internal"));
        assertThat(properties.getNamespace()).isEqualTo("team-a");
        assertThat(properties.getAuthentication()).isNotNull();
        assertThat(properties.getKv()).isNotNull();
        assertThat(properties.getTransit()).isNotNull();
        assertThat(properties.getTls()).isNotNull();
        assertThat(properties.getSecrets()).containsKey("orders");
        assertThat(properties.getSecrets()).isNotSameAs(mappings);

        properties.setSecrets(null);
        assertThat(properties.getSecrets()).isEmpty();
    }

    @Test
    void protectsAuthenticationAndTlsSecretArrays() {
        VaultSecretProperties.Authentication authentication = new VaultSecretProperties.Authentication();
        authentication.setType(null);
        authentication.setToken("token".toCharArray());
        authentication.setRoleId("role-id".toCharArray());
        authentication.setSecretId("secret-id".toCharArray());
        authentication.setJwt("jwt".toCharArray());
        authentication.setTokenFile("/run/token");
        authentication.setRole("orders");
        authentication.setKubernetesPath("kubernetes-custom");
        authentication.setServiceAccountTokenFile("/run/service-account");
        authentication.setAppRolePath("approle-custom");
        authentication.setRoleIdFile("/run/role-id");
        authentication.setSecretIdFile("/run/secret-id");
        authentication.setJwtPath("jwt-custom");
        authentication.setJwtRole("orders");
        authentication.setJwtFile("/run/jwt");

        char[] token = authentication.getToken();
        token[0] = 'X';
        assertThat(authentication.getToken()).containsExactly('t', 'o', 'k', 'e', 'n');
        assertThat(authentication.getRoleId()).containsExactly("role-id".toCharArray());
        assertThat(authentication.getSecretId()).containsExactly("secret-id".toCharArray());
        assertThat(authentication.getJwt()).containsExactly("jwt".toCharArray());
        assertThat(authentication.getType()).isEqualTo(VaultSecretProperties.AuthenticationType.KUBERNETES);
        assertThat(authentication.getTokenFile()).isEqualTo("/run/token");
        assertThat(authentication.getRole()).isEqualTo("orders");
        assertThat(authentication.getKubernetesPath()).isEqualTo("kubernetes-custom");
        assertThat(authentication.getServiceAccountTokenFile()).isEqualTo("/run/service-account");
        assertThat(authentication.getAppRolePath()).isEqualTo("approle-custom");
        assertThat(authentication.getRoleIdFile()).isEqualTo("/run/role-id");
        assertThat(authentication.getSecretIdFile()).isEqualTo("/run/secret-id");
        assertThat(authentication.getJwtPath()).isEqualTo("jwt-custom");
        assertThat(authentication.getJwtRole()).isEqualTo("orders");
        assertThat(authentication.getJwtFile()).isEqualTo("/run/jwt");

        VaultSecretProperties.Tls tls = new VaultSecretProperties.Tls();
        tls.setTrustStore("/run/ca.pem");
        tls.setTrustStorePassword("changeit".toCharArray());
        char[] password = tls.getTrustStorePassword();
        password[0] = 'X';
        assertThat(tls.getTrustStore()).isEqualTo("/run/ca.pem");
        assertThat(tls.getTrustStorePassword()).containsExactly("changeit".toCharArray());
        tls.setTrustStorePassword(null);
        assertThat(tls.getTrustStorePassword()).isEmpty();
    }

    @Test
    void exposesKvTransitAndMappingValues() {
        VaultSecretProperties.Kv kv = new VaultSecretProperties.Kv();
        kv.setMount("kv-v2");
        kv.setRuntimePrefix("runtime-prod");
        assertThat(kv.getMount()).isEqualTo("kv-v2");
        assertThat(kv.getRuntimePrefix()).isEqualTo("runtime-prod");

        VaultSecretProperties.Transit transit = new VaultSecretProperties.Transit();
        transit.setMount("transit-prod");
        transit.setKeyBindings(Map.of("orders", "orders-key"));
        assertThat(transit.getMount()).isEqualTo("transit-prod");
        assertThat(transit.getKeyBindings()).containsEntry("orders", "orders-key");
        transit.setKeyBindings(null);
        assertThat(transit.getKeyBindings()).isEmpty();

        VaultSecretProperties.SecretMapping mapping = new VaultSecretProperties.SecretMapping();
        mapping.setPath("orders");
        mapping.setField("password");
        assertThat(mapping.getPath()).isEqualTo("orders");
        assertThat(mapping.getField()).isEqualTo("password");
    }
}
