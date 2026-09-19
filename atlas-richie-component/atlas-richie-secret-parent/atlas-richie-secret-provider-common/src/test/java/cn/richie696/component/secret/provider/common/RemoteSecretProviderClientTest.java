package cn.richie696.component.secret.provider.common;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.api.SecretReference;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.WrappedKey;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteSecretProviderClientTest {

    @Test
    void defensivelyBindsLogicalKeyIdentityBeforeCallingTransport() {
        RemoteProviderProperties properties = new RemoteProviderProperties();
        properties.setEndpoint(URI.create("https://provider.example"));
        properties.setKeyBindings(Map.of("oauth", "physical-key"));
        CapturingTransport transport = new CapturingTransport();
        RemoteSecretProviderClient client = new RemoteSecretProviderClient(
                "test", "test", "hash", properties, new BootstrapSecretProperties(),
                Set.of(SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP), transport);
        KeyReference key = KeyReference.envelopeEncryption("oauth");

        WrappedKey wrapped = client.wrap(key, new byte[]{1, 2}, CryptoContext.empty());
        client.unwrap(key, wrapped, CryptoContext.empty());

        assertThat(transport.wrapContext.attributes())
                .containsEntry("atlas.secret.key", "oauth")
                .containsEntry("atlas.secret.version", "current")
                .containsEntry("atlas.secret.purpose", "ENVELOPE_ENCRYPTION");
        assertThat(transport.unwrapContext).isEqualTo(transport.wrapContext);
    }

    @Test
    void loadsFlattensReadsMetadataAndUsesDefaultLogicalPath() {
        RemoteProviderProperties properties = new RemoteProviderProperties();
        properties.setEndpoint(URI.create("https://provider.example"));
        properties.setSecrets(Map.of("database", mapping("orders/database", "password")));
        BootstrapSecretProperties bootstrap = new BootstrapSecretProperties();
        bootstrap.getPropertySource().setApplication("orders");
        bootstrap.getPropertySource().setEnvironment("prod");
        CapturingTransport transport = new CapturingTransport();
        transport.values.put("orders/database", new RemoteSecretTransport.RemoteValue(
                Map.of("password", "s3cret", "nested", Map.of("enabled", true)),
                "v1", Instant.parse("2026-01-01T00:00:00Z"), Map.of("source", "test")));
        transport.values.put("atlas-richie/prod/orders/runtime/runtime-key",
                new RemoteSecretTransport.RemoteValue("runtime", "v2", null, Map.of()));
        RemoteSecretProviderClient client = new RemoteSecretProviderClient("test", "test", "hash", properties,
                bootstrap, Set.of(SecretCapability.SECRET_READ), transport);

        var loaded = client.load(new SecretBootstrapRequest("orders", "prod", List.of("orders/database"), List.of()));
        try (var value = client.read(SecretReference.latest("database"))) {
            assertThat(value.copyChars()).containsExactly("s3cret".toCharArray());
        }
        var metadata = client.metadata(SecretReference.latest("database"));
        try (var fallback = client.read(SecretReference.latest("runtime-key"))) {
            assertThat(fallback.copyChars()).containsExactly("runtime".toCharArray());
        }

        assertThat(loaded.values()).containsEntry("nested.enabled", true);
        assertThat(metadata.attributes()).containsEntry("provider", "test").containsEntry("source", "test");
    }

    @Test
    void handlesLocalMissingPolicyAndRejectsUnsupportedOperations() {
        RemoteProviderProperties properties = new RemoteProviderProperties();
        properties.setEndpoint(URI.create("https://provider.example"));
        properties.setKeyBindings(Map.of("key", "physical"));
        BootstrapSecretProperties bootstrap = new BootstrapSecretProperties();
        bootstrap.getPropertySource().setMissingPolicy(BootstrapSecretProperties.MissingPolicy.LOCAL);
        CapturingTransport transport = new CapturingTransport();
        RemoteSecretProviderClient client = new RemoteSecretProviderClient("test", "test", "hash", properties,
                bootstrap, Set.of(SecretCapability.SECRET_READ, SecretCapability.KEY_WRAP), transport);

        assertThat(client.load(new SecretBootstrapRequest("orders", "prod", List.of("missing"), List.of()))
                .values()).isEmpty();
        assertThatThrownBy(() -> client.unwrap(KeyReference.envelopeEncryption("key"),
                new WrappedKey(new byte[]{1}, "wrong"), CryptoContext.empty()))
                .isInstanceOf(cn.richie696.component.secret.api.exception.SecretConfigurationException.class);
        assertThatThrownBy(() -> client.wrap(KeyReference.envelopeEncryption("missing"), new byte[]{1}, CryptoContext.empty()))
                .isInstanceOf(cn.richie696.component.secret.api.exception.SecretConfigurationException.class);
        assertThat(client.secretBackend()).contains(client);
        assertThat(client.keyWrappingBackend()).contains(client);
    }

    @Test
    void enforcesCapabilityAndLifecycleContracts() {
        RemoteProviderProperties properties = new RemoteProviderProperties();
        properties.setEndpoint(URI.create("https://provider.example"));
        CapturingTransport transport = new CapturingTransport();
        RemoteSecretProviderClient client = new RemoteSecretProviderClient("kms", "kms", "hash", properties,
                new BootstrapSecretProperties(), Set.of(SecretCapability.KEY_UNWRAP), transport);

        assertThat(client.secretBackend()).isEmpty();
        assertThat(client.keyWrappingBackend()).isEmpty();
        assertThatThrownBy(() -> client.read(SecretReference.latest("key")))
                .isInstanceOf(cn.richie696.component.secret.api.exception.SecretConfigurationException.class)
                .hasMessageContaining("SECRET_READ");
        assertThatThrownBy(() -> client.unwrap(KeyReference.envelopeEncryption("key"),
                new WrappedKey(new byte[]{1}, "kms-remote"), CryptoContext.empty()))
                .isInstanceOf(cn.richie696.component.secret.api.exception.SecretConfigurationException.class)
                .hasMessageContaining("key binding");
        client.close();
        client.close();
        assertThat(transport.closeCalls).hasValue(1);
        assertThatThrownBy(() -> client.load(new SecretBootstrapRequest("a", "b", List.of(), List.of())))
                .isInstanceOf(IllegalStateException.class);
    }

    private static RemoteProviderProperties.SecretMapping mapping(String path, String field) {
        RemoteProviderProperties.SecretMapping mapping = new RemoteProviderProperties.SecretMapping();
        mapping.setPath(path);
        mapping.setField(field);
        return mapping;
    }

    private static final class CapturingTransport implements RemoteSecretTransport {
        private CryptoContext wrapContext;
        private CryptoContext unwrapContext;
        private final Map<String, RemoteValue> values = new java.util.LinkedHashMap<>();
        private final AtomicInteger closeCalls = new AtomicInteger();

        @Override
        public RemoteValue read(String path, cn.richie696.component.secret.api.SecretVersionSelector selector) {
            return values.get(path);
        }

        @Override
        public byte[] wrap(String key, byte[] plaintext, CryptoContext context) {
            wrapContext = context;
            return plaintext.clone();
        }

        @Override
        public byte[] unwrap(String key, byte[] wrapped, CryptoContext context) {
            unwrapContext = context;
            return wrapped.clone();
        }

        @Override
        public void close() {
            closeCalls.incrementAndGet();
        }
    }
}
