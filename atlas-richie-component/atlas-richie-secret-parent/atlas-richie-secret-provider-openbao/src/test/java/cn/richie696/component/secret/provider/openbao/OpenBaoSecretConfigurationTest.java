package cn.richie696.component.secret.provider.openbao;

import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenBaoSecretConfigurationTest {

    @Test
    void validatesCompleteLoopbackConfigurationAndProducesStableHash() throws Exception {
        OpenBaoSecretProperties properties = new OpenBaoSecretProperties();
        properties.setEndpoint(URI.create("http://127.0.0.1:8200"));
        properties.setNamespace("orders");
        properties.getAuthentication().setToken("token".toCharArray());
        properties.getKv().setMount("secret-v2");
        properties.getKv().setRuntimePrefix("runtime-prod");
        properties.getTransit().setMount("transit-prod");
        properties.getTransit().setKeyBindings(Map.of("envelope", "envelope-key"));
        OpenBaoSecretProperties.SecretMapping mapping = new OpenBaoSecretProperties.SecretMapping();
        mapping.setPath("orders/database");
        mapping.setField("password");
        properties.setSecrets(Map.of("database", mapping));

        OpenBaoSecretConfiguration.validate(properties);
        assertThatHash(OpenBaoSecretConfiguration.hash("openbao", properties));
        assertThat(OpenBaoSecretConfiguration.hash("openbao", properties)).hasSize(64);
    }

    @Test
    void validatesTokenFileAndRejectsInvalidTransportSettings() throws Exception {
        Path token = Files.createTempFile("openbao-token", ".txt");
        Files.writeString(token, "token");
        OpenBaoSecretProperties properties = new OpenBaoSecretProperties();
        properties.setEndpoint(URI.create("https://openbao.example"));
        properties.getAuthentication().setType(OpenBaoSecretProperties.AuthenticationType.TOKEN_FILE);
        properties.getAuthentication().setTokenFile(token.toString());
        OpenBaoSecretConfiguration.validate(properties);

        properties.getProxy().setHost("proxy");
        properties.getProxy().setPort(70000);
        assertThatThrownBy(() -> OpenBaoSecretConfiguration.validate(properties))
                .isInstanceOf(SecretConfigurationException.class).hasMessageContaining("proxy");
        Files.deleteIfExists(token);
    }

    private static void assertThatHash(String hash) {
        if (hash == null || hash.isBlank()) throw new AssertionError("hash must be present");
    }
    @Test
    void rejectsNonLoopbackHttp() {
        OpenBaoSecretProperties p = new OpenBaoSecretProperties();
        p.setEndpoint(URI.create("http://openbao.example.internal"));
        assertThrows(RuntimeException.class, () -> OpenBaoSecretConfiguration.validate(p));
    }

    @Test
    void rejectsInvalidTransitKeyVersionInsteadOfSilentlyUsingCurrent() {
        OpenBaoSecretProperties properties = new OpenBaoSecretProperties();
        properties.setEndpoint(URI.create("http://127.0.0.1:1"));
        properties.getAuthentication().setToken("test-token".toCharArray());
        properties.getTransit().setKeyBindings(Map.of("oauth", "oauth-key"));
        BootstrapSecretProperties bootstrap = new BootstrapSecretProperties();
        bootstrap.getResilience().setMaxAttempts(1);
        try (OpenBaoSecretClient client = new OpenBaoSecretClient(
                "openbao", "hash", properties, bootstrap)) {
            assertThatThrownBy(() -> client.wrap(
                    KeyReference.envelopeEncryption("oauth", " 2"),
                    new byte[]{1},
                    CryptoContext.empty()))
                    .isInstanceOf(SecretConfigurationException.class)
                    .extracting(exception -> ((SecretConfigurationException) exception).errorCode())
                    .isEqualTo("SEC-KEY-003");
        }
    }
}
