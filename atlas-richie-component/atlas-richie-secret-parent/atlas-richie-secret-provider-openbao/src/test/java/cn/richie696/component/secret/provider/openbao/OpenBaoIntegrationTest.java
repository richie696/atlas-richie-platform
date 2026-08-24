package cn.richie696.component.secret.provider.openbao;

import cn.richie696.component.secret.api.SecretReference;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyPurpose;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.SignatureValue;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@EnabledIf("cn.richie696.component.secret.provider.openbao.OpenBaoIntegrationTest#isEnabled")
class OpenBaoIntegrationTest {
    @TempDir Path temporaryDirectory;

    static boolean isEnabled() {
        return truthy("ATLAS_SECRET_OPENBAO_E2E") && value("ATLAS_SECRET_OPENBAO_ENDPOINT") != null
                && value("ATLAS_SECRET_OPENBAO_TOKEN") != null
                && value("ATLAS_SECRET_OPENBAO_SECRET_PATH") != null
                && value("ATLAS_SECRET_OPENBAO_SIGNING_KEY") != null;
    }

    @Test
    void completesKvTransitAndSigningRoundTrip() throws Exception {
        Path tokenFile = temporaryDirectory.resolve("openbao-token");
        Files.writeString(tokenFile, value("ATLAS_SECRET_OPENBAO_TOKEN"));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.openbao.endpoint", value("ATLAS_SECRET_OPENBAO_ENDPOINT"))
                .withProperty("platform.component.secret.openbao.authentication.type", "token-file")
                .withProperty("platform.component.secret.openbao.authentication.token-file", tokenFile.toString())
                .withProperty("platform.component.secret.openbao.secrets.e2e.path", value("ATLAS_SECRET_OPENBAO_SECRET_PATH"))
                .withProperty("platform.component.secret.openbao.transit.key-bindings.e2e-signing", value("ATLAS_SECRET_OPENBAO_SIGNING_KEY"));
        BootstrapSecretProperties bootstrap = new BootstrapSecretProperties();
        bootstrap.setEnabled(true);
        try (OpenBaoSecretClient client = (OpenBaoSecretClient) new OpenBaoSecretBootstrapProviderFactory()
                .create(bootstrap, new SecretBootstrapContext(environment, getClass().getClassLoader()))) {
            try (var secret = client.read(SecretReference.latest("e2e"))) {
                byte[] value = secret.copyBytes();
                try { assertThat(value).isNotEmpty(); } finally { Arrays.fill(value, (byte) 0); }
            }
            KeyReference key = new KeyReference("e2e-signing", "current", KeyPurpose.SIGNING);
            byte[] payload = "atlas-openbao-signing-e2e".getBytes();
            try {
                SignatureValue signature = client.sign(key, payload, CryptoContext.empty());
                assertThat(client.verify(key, payload, signature, CryptoContext.empty())).isTrue();
            } finally { Arrays.fill(payload, (byte) 0); }
        }
    }

    private static String value(String name) { String value = System.getenv(name); return value == null || value.isBlank() ? null : value; }
    private static boolean truthy(String name) { String value = value(name); return value != null && Boolean.parseBoolean(value); }
}
