package cn.richie696.component.secret.provider.vault;

import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyPurpose;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.SignatureValue;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.testing.env.TestEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies Transit sign/verify without exporting the private key. */
@Tag("integration")
@EnabledIf("cn.richie696.component.secret.provider.vault.VaultTransitSigningIntegrationTest#isEnabled")
class VaultTransitSigningIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    static boolean isEnabled() {
        return TestEnv.isTruthy("ATLAS_SECRET_VAULT_E2E", "atlas.secret.vault.e2e")
                && VaultIntegrationTestSupport.token() != null
                && VaultIntegrationTestSupport.signingKey() != null;
    }

    @Test
    void signsAndVerifiesWithTransitKey() throws Exception {
        Path tokenFile = temporaryDirectory.resolve("vault-signing-token");
        Files.writeString(tokenFile, VaultIntegrationTestSupport.token());
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.vault.endpoint", VaultIntegrationTestSupport.endpoint())
                .withProperty("platform.component.secret.vault.authentication.type", "token-file")
                .withProperty("platform.component.secret.vault.authentication.token-file", tokenFile.toString())
                .withProperty("platform.component.secret.vault.transit.mount", "transit")
                .withProperty("platform.component.secret.vault.transit.key-bindings.e2e-signing",
                        VaultIntegrationTestSupport.signingKey());
        BootstrapSecretProperties properties = new BootstrapSecretProperties();
        properties.setEnabled(true);
        properties.getResilience().setMaxAttempts(1);
        VaultSecretClient client = (VaultSecretClient) new VaultSecretBootstrapProviderFactory().create(
                properties, new SecretBootstrapContext(environment, getClass().getClassLoader()));
        try (client) {
            KeyReference key = new KeyReference("e2e-signing", "current", KeyPurpose.SIGNING);
            byte[] payload = "atlas-secret-signing-e2e".getBytes();
            SignatureValue signature = client.sign(key, payload, CryptoContext.empty());
            assertThat(client.verify(key, payload, signature, CryptoContext.empty())).isTrue();
        }
    }
}
