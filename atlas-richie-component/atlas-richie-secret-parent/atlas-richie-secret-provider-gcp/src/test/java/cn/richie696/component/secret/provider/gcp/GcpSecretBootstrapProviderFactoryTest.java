package cn.richie696.component.secret.provider.gcp;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.provider.common.RemoteSecretProviderClient;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class GcpSecretBootstrapProviderFactoryTest {
    @Test
    void advertisesSecretManagerAndCloudKmsCapabilities() {
        GcpSecretBootstrapProviderFactory factory = new GcpSecretBootstrapProviderFactory();

        assertThat(factory.providerType()).isEqualTo("gcp");
        assertThat(factory.capabilities()).containsExactlyInAnyOrder(
                SecretCapability.SECRET_READ,
                SecretCapability.SECRET_VERSIONING,
                SecretCapability.KEY_WRAP,
                SecretCapability.KEY_UNWRAP);
    }

    @Test
    void createsSdkBackedClientWithConfiguredProject() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.gcp.endpoint", "https://gcp.example")
                .withProperty("platform.component.secret.gcp.project-id", "project-a");
        var context = new SecretBootstrapContext(environment, getClass().getClassLoader(),
                "gcp", "gcp", "platform.component.secret.gcp");
        SecretManagerServiceClient secrets = mock(SecretManagerServiceClient.class);
        KeyManagementServiceClient kms = mock(KeyManagementServiceClient.class);

        try (var secretFactory = mockStatic(SecretManagerServiceClient.class);
             var kmsFactory = mockStatic(KeyManagementServiceClient.class)) {
            secretFactory.when(SecretManagerServiceClient::create).thenReturn(secrets);
            kmsFactory.when(KeyManagementServiceClient::create).thenReturn(kms);
            try (var client = (RemoteSecretProviderClient) new GcpSecretBootstrapProviderFactory().create(
                    new BootstrapSecretProperties(), context)) {
                assertThat(client.descriptor().providerType()).isEqualTo("gcp");
            }
            verify(secrets).close();
            verify(kms).close();
        }
    }
}
