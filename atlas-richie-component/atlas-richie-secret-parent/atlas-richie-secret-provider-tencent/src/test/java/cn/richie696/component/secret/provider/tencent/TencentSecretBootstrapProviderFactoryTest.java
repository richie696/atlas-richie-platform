package cn.richie696.component.secret.provider.tencent;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.provider.common.RemoteSecretProviderClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class TencentSecretBootstrapProviderFactoryTest {
    @Test
    void advertisesSsmAndKmsCapabilities() {
        TencentSecretBootstrapProviderFactory factory = new TencentSecretBootstrapProviderFactory();

        assertThat(factory.providerType()).isEqualTo("tencent");
        assertThat(factory.capabilities()).containsExactlyInAnyOrder(
                SecretCapability.SECRET_READ,
                SecretCapability.SECRET_VERSIONING,
                SecretCapability.KEY_WRAP,
                SecretCapability.KEY_UNWRAP);
    }

    @Test
    void createsSdkBackedClientFromAccessKeyConfiguration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.tencent.endpoint", "https://tencent.example")
                .withProperty("platform.component.secret.tencent.region", "ap-guangzhou")
                .withProperty("platform.component.secret.tencent.authentication.type", "access-key")
                .withProperty("platform.component.secret.tencent.authentication.access-key-id", "id")
                .withProperty("platform.component.secret.tencent.authentication.access-key-secret", "secret");
        var context = new SecretBootstrapContext(environment, getClass().getClassLoader(),
                "tencent", "tencent", "platform.component.secret.tencent");

        try (var client = (RemoteSecretProviderClient) new TencentSecretBootstrapProviderFactory().create(
                new BootstrapSecretProperties(), context)) {
            assertThat(client.descriptor().providerType()).isEqualTo("tencent");
        }
    }
}
