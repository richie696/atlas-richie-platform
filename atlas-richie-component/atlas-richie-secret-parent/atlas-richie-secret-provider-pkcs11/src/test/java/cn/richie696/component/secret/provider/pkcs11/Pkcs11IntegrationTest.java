package cn.richie696.component.secret.provider.pkcs11;

import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.SignatureValue;
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
@EnabledIf("cn.richie696.component.secret.provider.pkcs11.Pkcs11IntegrationTest#isEnabled")
class Pkcs11IntegrationTest {
    static boolean isEnabled() {
        return truthy("ATLAS_SECRET_PKCS11_E2E") && value("ATLAS_SECRET_PKCS11_LIBRARY") != null
                && value("ATLAS_SECRET_PKCS11_PIN") != null
                && (value("ATLAS_SECRET_PKCS11_KEY_ALIAS") != null
                || (value("ATLAS_SECRET_PKCS11_WRAP_KEY_ALIAS") != null
                && value("ATLAS_SECRET_PKCS11_SIGNING_KEY_ALIAS") != null));
    }

    @Test
    void completesHsmWrapAndNonExportedSigningRoundTrip() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.pkcs11.library", value("ATLAS_SECRET_PKCS11_LIBRARY"))
                .withProperty("platform.component.secret.pkcs11.pin", value("ATLAS_SECRET_PKCS11_PIN"))
                .withProperty("platform.component.secret.pkcs11.key-bindings.e2e-wrap", first("ATLAS_SECRET_PKCS11_WRAP_KEY_ALIAS", "ATLAS_SECRET_PKCS11_KEY_ALIAS"))
                .withProperty("platform.component.secret.pkcs11.key-bindings.e2e-signing", first("ATLAS_SECRET_PKCS11_SIGNING_KEY_ALIAS", "ATLAS_SECRET_PKCS11_KEY_ALIAS"));
        if (value("ATLAS_SECRET_PKCS11_SLOT") != null) environment.withProperty("platform.component.secret.pkcs11.slot", value("ATLAS_SECRET_PKCS11_SLOT"));
        if (value("ATLAS_SECRET_PKCS11_TOKEN_LABEL") != null) environment.withProperty("platform.component.secret.pkcs11.token-label", value("ATLAS_SECRET_PKCS11_TOKEN_LABEL"));
        BootstrapSecretProperties bootstrap = new BootstrapSecretProperties();
        bootstrap.setEnabled(true);
        try (Pkcs11SecretClient client = (Pkcs11SecretClient) new Pkcs11SecretBootstrapProviderFactory()
                .create(bootstrap, new SecretBootstrapContext(environment, getClass().getClassLoader()))) {
            KeyReference encryption = KeyReference.envelopeEncryption("e2e-wrap");
            byte[] plaintext = "0123456789abcdef".getBytes();
            try {
                WrappedKey wrapped = client.wrap(encryption, plaintext, CryptoContext.empty());
                byte[] restored = client.unwrap(encryption, wrapped, CryptoContext.empty());
                try { assertThat(restored).isEqualTo(plaintext); } finally { Arrays.fill(restored, (byte) 0); }
            } finally { Arrays.fill(plaintext, (byte) 0); }
            KeyReference signing = new KeyReference("e2e-signing", "current", cn.richie696.component.secret.api.crypto.KeyPurpose.SIGNING);
            byte[] payload = "atlas-pkcs11-signing-e2e".getBytes();
            try {
                SignatureValue signature = client.sign(signing, payload, CryptoContext.empty());
                assertThat(client.verify(signing, payload, signature, CryptoContext.empty())).isTrue();
            } finally { Arrays.fill(payload, (byte) 0); }
        }
    }

    private static String value(String name) { String value = System.getenv(name); return value == null || value.isBlank() ? null : value; }
    private static String first(String primary, String fallback) {
        String value = value(primary);
        return value == null ? value(fallback) : value;
    }
    private static boolean truthy(String name) { String value = value(name); return value != null && Boolean.parseBoolean(value); }
}
