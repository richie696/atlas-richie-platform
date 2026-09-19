package cn.richie696.component.secret.provider.azure;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.provider.common.RemoteSecretProviderClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class AzureSecretBootstrapProviderFactoryTest {
    @Test
    void advertisesOnlyAzureKeyVaultCapabilities() {
        AzureSecretBootstrapProviderFactory factory = new AzureSecretBootstrapProviderFactory();

        assertThat(factory.providerType()).isEqualTo("azure");
        assertThat(factory.capabilities()).containsExactlyInAnyOrder(
                SecretCapability.SECRET_READ,
                SecretCapability.SECRET_VERSIONING,
                SecretCapability.KEY_WRAP,
                SecretCapability.KEY_UNWRAP);
    }

    @Test
    void createsSdkBackedClientWithDefaultAzureIdentityConfiguration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.azure.endpoint", "https://orders.vault.azure.net");
        var context = new SecretBootstrapContext(environment, getClass().getClassLoader(),
                "azure", "azure", "platform.component.secret.azure");

        try (var client = (RemoteSecretProviderClient) new AzureSecretBootstrapProviderFactory().create(
                new BootstrapSecretProperties(), context)) {
            assertThat(client.descriptor().providerType()).isEqualTo("azure");
            assertThat(client.descriptor().capabilities()).contains(SecretCapability.KEY_WRAP);
        }
    }
}
