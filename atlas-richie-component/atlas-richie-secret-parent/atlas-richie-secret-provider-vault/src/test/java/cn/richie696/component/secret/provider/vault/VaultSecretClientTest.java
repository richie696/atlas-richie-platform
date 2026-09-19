/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.vault;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.api.SecretMetadata;
import cn.richie696.component.secret.api.SecretReference;
import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.KeyPurpose;
import cn.richie696.component.secret.api.crypto.WrappedKey;
import cn.richie696.component.secret.api.exception.SecretBootstrapException;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.api.exception.SecretException;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTransitOperations;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.support.Versioned;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VaultSecretClientTest {
    private VaultTestBackend backend;
    private VaultSecretClient client;
    private AtomicBoolean closed;

    @BeforeEach
    void setUp() {
        VaultSecretProperties properties = new VaultSecretProperties();
        properties.setEndpoint(java.net.URI.create("http://127.0.0.1:8200"));
        VaultSecretProperties.Transit transitProperties = new VaultSecretProperties.Transit();
        transitProperties.setKeyBindings(Map.of("default-envelope", "orders-physical-key"));
        properties.setTransit(transitProperties);
        VaultSecretProperties.SecretMapping mapping = new VaultSecretProperties.SecretMapping();
        mapping.setPath("applications/orders/database");
        mapping.setField("password");
        properties.setSecrets(Map.of("database-password", mapping));

        BootstrapSecretProperties bootstrap = new BootstrapSecretProperties();
        bootstrap.getPropertySource().setApplication("orders");
        bootstrap.getPropertySource().setEnvironment("prod");
        bootstrap.getResilience().setMaxAttempts(1);
        var resolved = new VaultSecretConfigurationResolver.ResolvedVaultConfiguration(
                "vault", properties, "configuration-hash");

        backend = new VaultTestBackend();
        closed = new AtomicBoolean();
        client = new VaultSecretClient(resolved, bootstrap, backend.operations(), () -> closed.set(true));
    }

    @Test
    void mergesAndFlattensBootstrapBundles() {
        backend.put("atlas-richie/prod/orders/common",
                versioned(Map.of("platform", Map.of("oauth", Map.of("secret", "one"))), 3));
        backend.put("atlas-richie/prod/orders/components",
                versioned(Map.of("platform.component.storage.token", "two"), 7));

        var result = client.load(new SecretBootstrapRequest(
                "orders",
                "prod",
                java.util.List.of(
                        "atlas-richie/prod/orders/common",
                        "atlas-richie/prod/orders/components"),
                java.util.List.of()));

        assertThat(result.providerId()).isEqualTo("vault");
        assertThat(result.version()).hasSize(64);
        assertThat(result.values())
                .containsEntry("platform.oauth.secret", "one")
                .containsEntry("platform.component.storage.token", "two");
    }

    @Test
    void failsClosedWhenBundlesConflict() {
        backend.put("one", versioned(Map.of("shared.key", "first"), 1));
        backend.put("two", versioned(Map.of("shared.key", "second"), 2));

        assertThatThrownBy(() -> client.load(new SecretBootstrapRequest(
                "orders", "prod", java.util.List.of("one", "two"), java.util.List.of())))
                .isInstanceOf(SecretBootstrapException.class)
                .hasMessageContaining("conflicting");
    }

    @Test
    void resolvesLogicalSecretThroughConfiguredPhysicalMapping() {
        backend.put("applications/orders/database",
                versioned(Map.of("username", "orders", "password", "s3cret"), 4));

        try (var value = client.read(SecretReference.latest("database-password"))) {
            assertThat(value.copyChars()).containsExactly("s3cret".toCharArray());
        }
        assertThat(backend.lastKvPath).isEqualTo("applications/orders/database");
    }

    @Test
    void wrapsAndUnwrapsUsingPhysicalTransitKeyOnlyInsideProvider() {
        byte[] dataKey = "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8);
        backend.transitCiphertext = "vault:v3:ciphertext";
        backend.transitPlaintext = dataKey.clone();

        WrappedKey wrapped = client.wrap(
                KeyReference.envelopeEncryption("default-envelope"),
                dataKey,
                CryptoContext.empty());
        byte[] unwrapped = client.unwrap(
                KeyReference.envelopeEncryption("default-envelope"),
                wrapped,
                CryptoContext.empty());

        assertThat(wrapped.algorithm()).isEqualTo("vault-transit");
        assertThat(unwrapped).isEqualTo(dataKey);
        assertThat(backend.lastTransitKey).isEqualTo("orders-physical-key");
    }

    @Test
    void refusesAnUnmappedLogicalKeyInsteadOfTreatingItAsPhysical() {
        assertThatThrownBy(() -> client.wrap(
                KeyReference.envelopeEncryption("unknown-key"),
                new byte[32],
                CryptoContext.empty()))
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("logical key unknown-key");
    }

    @Test
    void closesOwnedSdkResourcesExactlyOnce() {
        client.close();
        client.close();

        assertThat(closed).isTrue();
        assertThatThrownBy(() -> client.read(SecretReference.latest("database-password")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void exposesMetadataAndSupportsNumericVersionSelectors() {
        backend.put("applications/orders/database",
                versioned(Map.of("username", "orders", "password", "s3cret"), 4));

        SecretMetadata metadata = client.metadata(SecretReference.latest("database-password"));
        assertThat(metadata.version()).isEqualTo("4");
        assertThat(metadata.attributes()).containsEntry("provider", "vault");

        SecretReference versionedReference = new SecretReference(
                "database-password", SecretVersionSelector.version("2"), null);
        assertThat(client.read(versionedReference).copyChars())
                .containsExactly("s3cret".toCharArray());
    }

    @Test
    void usesDefaultRuntimePathAndScalarConversions() {
        backend.put("atlas-richie/prod/orders/runtime/simple",
                versioned(Map.of("value", 42), 1));
        backend.put("atlas-richie/prod/orders/runtime/bytes",
                versioned(Map.of("value", "raw".getBytes(StandardCharsets.UTF_8)), 1));

        assertThat(client.read(SecretReference.latest("simple")).copyChars())
                .containsExactly("42".toCharArray());
        assertThat(client.read(SecretReference.latest("bytes")).copyBytes())
                .containsExactly("raw".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsAmbiguousAndInvalidSecretSelectors() {
        backend.put("atlas-richie/prod/orders/runtime/ambiguous",
                versioned(Map.of("one", "1", "two", "2"), 1));
        assertThatThrownBy(() -> client.read(SecretReference.latest("ambiguous")))
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("multiple fields");

        SecretReference invalidVersion = new SecretReference(
                "database-password", SecretVersionSelector.version("0"), null);
        assertThatThrownBy(() -> client.read(invalidVersion))
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("positive integer");
        backend.put("applications/orders/database",
                versioned(Map.of("password", "s3cret"), 1));
        assertThatThrownBy(() -> client.read(SecretReference.latestField("database-password", "missing")))
                .isInstanceOf(SecretException.class)
                .hasMessageContaining("field is missing");
    }

    @Test
    void validatesWrappingAndSigningContracts() {
        assertThatThrownBy(() -> client.wrap(
                KeyReference.envelopeEncryption("default-envelope"), new byte[0], CryptoContext.empty()))
                .isInstanceOf(SecretCryptoException.class);
        assertThatThrownBy(() -> client.unwrap(
                KeyReference.envelopeEncryption("default-envelope"),
                new WrappedKey("vault:v1:ciphertext".getBytes(StandardCharsets.UTF_8), "other"),
                CryptoContext.empty()))
                .isInstanceOf(SecretCryptoException.class);
        assertThatThrownBy(() -> client.unwrap(
                KeyReference.envelopeEncryption("default-envelope"),
                new WrappedKey("not-vault".getBytes(StandardCharsets.UTF_8), "vault-transit"),
                CryptoContext.empty()))
                .isInstanceOf(SecretCryptoException.class);

        KeyReference encryptionKey = KeyReference.envelopeEncryption("default-envelope");
        assertThatThrownBy(() -> client.sign(encryptionKey, new byte[]{1}, CryptoContext.empty()))
                .isInstanceOf(SecretConfigurationException.class);
        KeyReference signingKey = new KeyReference("oauth.signing", "current", KeyPurpose.SIGNING);
        assertThatThrownBy(() -> client.sign(signingKey, new byte[0], CryptoContext.empty()))
                .isInstanceOf(SecretCryptoException.class);
        assertThatThrownBy(() -> client.verify(signingKey, new byte[]{1}, null, CryptoContext.empty()))
                .isInstanceOf(SecretCryptoException.class);
    }

    @Test
    void exposesProviderDescriptorAndOptionalBackends() {
        assertThat(client.descriptor().providerType()).isEqualTo("vault");
        assertThat(client.descriptor().capabilities()).contains(SecretCapability.SECRET_READ);
        assertThat(client.secretBackend()).contains(client);
        assertThat(client.keyWrappingBackend()).contains(client);
        assertThat(client.signingBackend()).contains(client);
        assertThat(client.configurationHash()).isEqualTo("configuration-hash");
    }

    private Versioned<Map<String, Object>> versioned(Map<String, Object> data, int version) {
        Versioned.Version value = Versioned.Version.from(version);
        return Versioned.create(data, Versioned.Metadata.builder()
                .version(value)
                .createdAt(Instant.parse("2026-08-22T00:00:00Z"))
                .build());
    }

    private static final class VaultTestBackend {
        private final Map<String, Versioned<Map<String, Object>>> values = new LinkedHashMap<>();
        private String lastKvPath;
        private String lastTransitKey;
        private String transitCiphertext;
        private byte[] transitPlaintext;

        void put(String path, Versioned<Map<String, Object>> value) {
            values.put(path, value);
        }

        VaultOperations operations() {
            VaultVersionedKeyValueOperations kv = proxy(
                    VaultVersionedKeyValueOperations.class,
                    (method, arguments) -> {
                        if ("get".equals(method.getName())) {
                            lastKvPath = (String) arguments[0];
                            return values.get(lastKvPath);
                        }
                        return defaultValue(method.getReturnType());
                    });
            VaultTransitOperations transit = proxy(
                    VaultTransitOperations.class,
                    (method, arguments) -> {
                        if ("encrypt".equals(method.getName())) {
                            lastTransitKey = (String) arguments[0];
                            return transitCiphertext;
                        }
                        if ("decrypt".equals(method.getName())) {
                            lastTransitKey = (String) arguments[0];
                            return transitPlaintext.clone();
                        }
                        return defaultValue(method.getReturnType());
                    });
            return proxy(VaultOperations.class, (method, arguments) -> switch (method.getName()) {
                case "opsForVersionedKeyValue" -> kv;
                case "opsForTransit" -> transit;
                default -> defaultValue(method.getReturnType());
            });
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive() || type == void.class) {
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == char.class) {
                return '\0';
            }
            return 0;
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, Invocation invocation) {
            return (T) Proxy.newProxyInstance(
                    type.getClassLoader(),
                    new Class<?>[]{type},
                    (proxy, method, arguments) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return switch (method.getName()) {
                                case "toString" -> type.getSimpleName() + "TestProxy";
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "equals" -> proxy == arguments[0];
                                default -> null;
                            };
                        }
                        return invocation.invoke(method, arguments == null ? new Object[0] : arguments);
                    });
        }

        @FunctionalInterface
        private interface Invocation {
            Object invoke(Method method, Object[] arguments);
        }
    }
}
