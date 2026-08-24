package cn.richie696.component.secret.provider.vault;

import cn.richie696.component.secret.api.exception.SecretException;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import cn.richie696.testing.env.TestEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real Vault policy-denial gate; skipped until a deliberately restricted token is supplied. */
@Tag("integration")
@EnabledIf("cn.richie696.component.secret.provider.vault.VaultPermissionIntegrationTest#isEnabled")
class VaultPermissionIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    static boolean isEnabled() {
        return TestEnv.isTruthy("ATLAS_SECRET_VAULT_E2E", "atlas.secret.vault.e2e")
                && VaultIntegrationTestSupport.deniedToken() != null;
    }

    @Test
    void restrictedTokenMapsToAuthorizationError() throws Exception {
        Path tokenFile = temporaryDirectory.resolve("vault-denied-token");
        Files.writeString(tokenFile, VaultIntegrationTestSupport.deniedToken());
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.vault.endpoint", VaultIntegrationTestSupport.endpoint())
                .withProperty("platform.component.secret.vault.authentication.type", "token-file")
                .withProperty("platform.component.secret.vault.authentication.token-file", tokenFile.toString())
                .withProperty("platform.component.secret.vault.kv.mount", "kv");
        BootstrapSecretProperties properties = new BootstrapSecretProperties();
        properties.setEnabled(true);
        properties.getPropertySource().setApplication("orders");
        properties.getPropertySource().setEnvironment("e2e");
        properties.getResilience().setMaxAttempts(1);
        VaultSecretClient client = (VaultSecretClient) new VaultSecretBootstrapProviderFactory().create(
                properties, new SecretBootstrapContext(environment, getClass().getClassLoader()));
        try (client) {
            assertThatThrownBy(() -> client.load(new SecretBootstrapRequest(
                    "orders", "e2e", List.of("atlas-richie/e2e/denied"), List.of())))
                    .isInstanceOf(SecretException.class)
                    .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((SecretException) error).errorCode())
                            .isIn("SEC-AUTH-001", "SEC-AUTHZ-001"));
        }
    }
}
