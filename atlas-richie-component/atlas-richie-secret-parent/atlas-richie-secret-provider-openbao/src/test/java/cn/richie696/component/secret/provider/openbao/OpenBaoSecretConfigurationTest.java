package cn.richie696.component.secret.provider.openbao;

import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenBaoSecretConfigurationTest {
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
