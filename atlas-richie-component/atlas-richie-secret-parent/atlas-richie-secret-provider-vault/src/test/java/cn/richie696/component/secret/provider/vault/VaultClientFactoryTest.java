package cn.richie696.component.secret.provider.vault;

import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class VaultClientFactoryTest {

    @Test
    void createsAndClosesClientsForSupportedAuthenticationModes() throws Exception {
        for (VaultSecretProperties.AuthenticationType type : VaultSecretProperties.AuthenticationType.values()) {
            VaultSecretProperties properties = new VaultSecretProperties();
            properties.setEndpoint(URI.create("http://127.0.0.1:8200"));
            configureAuthentication(properties, type);
            BootstrapSecretProperties bootstrap = new BootstrapSecretProperties();
            bootstrap.getResilience().setMaxAttempts(1);
            var resolved = new VaultSecretConfigurationResolver.ResolvedVaultConfiguration(
                    "vault", properties, "hash-" + type.name());

            VaultSecretClient client = new VaultClientFactory().create(resolved, bootstrap);
            assertThat(client.descriptor().providerType()).isEqualTo("vault");
            client.close();
        }
    }

    private void configureAuthentication(
            VaultSecretProperties properties,
            VaultSecretProperties.AuthenticationType type) throws Exception {
        properties.getAuthentication().setType(type);
        switch (type) {
            case TOKEN -> properties.getAuthentication().setToken("token".toCharArray());
            case TOKEN_FILE -> {
                var file = Files.createTempFile("vault-token", ".txt");
                Files.writeString(file, "token");
                properties.getAuthentication().setTokenFile(file.toString());
            }
            case APPROLE -> {
                properties.getAuthentication().setRoleId("role-id".toCharArray());
                properties.getAuthentication().setSecretId("secret-id".toCharArray());
            }
            case JWT -> {
                properties.getAuthentication().setJwtRole("role");
                properties.getAuthentication().setJwt("jwt".toCharArray());
            }
            case KUBERNETES -> properties.getAuthentication().setRole("role");
            case AGENT -> {
                // Agent mode intentionally creates no authentication session manager.
            }
        }
    }
}
