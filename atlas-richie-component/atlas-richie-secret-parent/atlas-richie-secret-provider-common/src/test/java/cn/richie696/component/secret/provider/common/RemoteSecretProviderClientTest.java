package cn.richie696.component.secret.provider.common;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.WrappedKey;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static final class CapturingTransport implements RemoteSecretTransport {
        private CryptoContext wrapContext;
        private CryptoContext unwrapContext;

        @Override
        public RemoteValue read(String path, cn.richie696.component.secret.api.SecretVersionSelector selector) {
            throw new UnsupportedOperationException("not needed");
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
    }
}
