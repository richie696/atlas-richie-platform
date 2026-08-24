package cn.richie696.component.secret.provider.kmip;

import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.WrappedKey;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@EnabledIf("cn.richie696.component.secret.provider.kmip.KmipIntegrationTest#isEnabled")
class KmipIntegrationTest {
    static boolean isEnabled() {
        return truthy("ATLAS_SECRET_KMIP_E2E") && value("ATLAS_SECRET_KMIP_ENDPOINT") != null
                && value("ATLAS_SECRET_KMIP_KEY_ALIAS") != null && value("ATLAS_SECRET_KMIP_TRUST_STORE") != null;
    }

    @Test
    void completesKmipEncryptDecryptRoundTrip() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.kmip.endpoint", value("ATLAS_SECRET_KMIP_ENDPOINT"))
                .withProperty("platform.component.secret.kmip.trust-store", value("ATLAS_SECRET_KMIP_TRUST_STORE"))
                .withProperty("platform.component.secret.kmip.trust-store-password", value("ATLAS_SECRET_KMIP_TRUST_STORE_PASSWORD"))
                .withProperty("platform.component.secret.kmip.key-bindings.e2e", value("ATLAS_SECRET_KMIP_KEY_ALIAS"));
        if (value("ATLAS_SECRET_KMIP_PROTOCOL_MINOR") != null) {
            environment.withProperty("platform.component.secret.kmip.protocol-minor", value("ATLAS_SECRET_KMIP_PROTOCOL_MINOR"));
        }
        if (value("ATLAS_SECRET_KMIP_KEY_STORE") != null) {
            environment.withProperty("platform.component.secret.kmip.key-store", value("ATLAS_SECRET_KMIP_KEY_STORE"))
                    .withProperty("platform.component.secret.kmip.key-store-password", value("ATLAS_SECRET_KMIP_KEY_STORE_PASSWORD"));
        }
        BootstrapSecretProperties bootstrap = new BootstrapSecretProperties();
        bootstrap.setEnabled(true);
        try (KmipSecretClient client = (KmipSecretClient) new KmipSecretBootstrapProviderFactory()
                .create(bootstrap, new SecretBootstrapContext(environment, getClass().getClassLoader()))) {
            byte[] plaintext = "atlas-kmip-e2e".getBytes();
            try {
                KeyReference key = KeyReference.envelopeEncryption("e2e");
                WrappedKey wrapped = client.wrap(key, plaintext, CryptoContext.empty());
                byte[] restored = client.unwrap(key, wrapped, CryptoContext.empty());
                try { assertThat(restored).isEqualTo(plaintext); } finally { Arrays.fill(restored, (byte) 0); }
            } finally { Arrays.fill(plaintext, (byte) 0); }
        }
    }

    private static String value(String name) { String value = System.getenv(name); return value == null || value.isBlank() ? null : value; }
    private static boolean truthy(String name) { String value = value(name); return value != null && Boolean.parseBoolean(value); }
}
