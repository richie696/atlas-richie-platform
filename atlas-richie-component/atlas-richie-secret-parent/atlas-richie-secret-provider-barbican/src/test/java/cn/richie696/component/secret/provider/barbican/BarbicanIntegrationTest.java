package cn.richie696.component.secret.provider.barbican;

import cn.richie696.component.secret.api.SecretReference;
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
@EnabledIf("cn.richie696.component.secret.provider.barbican.BarbicanIntegrationTest#isEnabled")
class BarbicanIntegrationTest {
    @TempDir Path temporaryDirectory;

    static boolean isEnabled() {
        return truthy("ATLAS_SECRET_BARBICAN_E2E") && value("ATLAS_SECRET_BARBICAN_ENDPOINT") != null
                && value("ATLAS_SECRET_BARBICAN_TOKEN") != null && value("ATLAS_SECRET_BARBICAN_SECRET_ID") != null;
    }

    @Test
    void readsSecretPayloadAndMetadata() throws Exception {
        Path tokenFile = temporaryDirectory.resolve("barbican-token");
        Files.writeString(tokenFile, value("ATLAS_SECRET_BARBICAN_TOKEN"));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.barbican.endpoint", value("ATLAS_SECRET_BARBICAN_ENDPOINT"))
                .withProperty("platform.component.secret.barbican.authentication.type", "token-file")
                .withProperty("platform.component.secret.barbican.authentication.token-file", tokenFile.toString())
                .withProperty("platform.component.secret.barbican.secrets.e2e.id", value("ATLAS_SECRET_BARBICAN_SECRET_ID"));
        String projectId = value("ATLAS_SECRET_BARBICAN_PROJECT_ID");
        if (projectId != null) {
            environment.withProperty("platform.component.secret.barbican.project-id", projectId);
        }
        BootstrapSecretProperties bootstrap = new BootstrapSecretProperties();
        bootstrap.setEnabled(true);
        try (BarbicanSecretClient client = (BarbicanSecretClient) new BarbicanSecretBootstrapProviderFactory()
                .create(bootstrap, new SecretBootstrapContext(environment, getClass().getClassLoader()))) {
            try (var value = client.read(SecretReference.latest("e2e"))) {
                byte[] payload = value.copyBytes();
                try { assertThat(payload).isNotEmpty(); } finally { Arrays.fill(payload, (byte) 0); }
            }
            assertThat(client.metadata(SecretReference.latest("e2e")).version()).isNotBlank();
        }
    }

    private static String value(String name) { String value = System.getenv(name); return value == null || value.isBlank() ? null : value; }
    private static boolean truthy(String name) { String value = value(name); return value != null && Boolean.parseBoolean(value); }
}
