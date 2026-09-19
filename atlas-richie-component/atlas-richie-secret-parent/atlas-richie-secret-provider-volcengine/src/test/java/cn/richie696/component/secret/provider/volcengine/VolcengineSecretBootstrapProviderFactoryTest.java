package cn.richie696.component.secret.provider.volcengine;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.provider.common.RemoteSecretProviderClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class VolcengineSecretBootstrapProviderFactoryTest {
    @Test
    void advertisesOnlyKmsCapabilities() {
        VolcengineSecretBootstrapProviderFactory factory = new VolcengineSecretBootstrapProviderFactory();

        assertThat(factory.providerType()).isEqualTo("volcengine");
        assertThat(factory.capabilities()).containsExactlyInAnyOrder(
                SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP);
    }

    @Test
    void createsKmsOnlyClientFromAccessKeyConfiguration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.volcengine.endpoint", "https://volcengine.example")
                .withProperty("platform.component.secret.volcengine.region", "cn-beijing")
                .withProperty("platform.component.secret.volcengine.namespace", "orders")
                .withProperty("platform.component.secret.volcengine.authentication.type", "access-key")
                .withProperty("platform.component.secret.volcengine.authentication.access-key-id", "ak")
                .withProperty("platform.component.secret.volcengine.authentication.access-key-secret", "sk");
        var context = new SecretBootstrapContext(environment, getClass().getClassLoader(),
                "volcengine", "volcengine", "platform.component.secret.volcengine");

        try (var client = (RemoteSecretProviderClient) new VolcengineSecretBootstrapProviderFactory().create(
                new BootstrapSecretProperties(), context)) {
            assertThat(client.descriptor().providerType()).isEqualTo("volcengine");
            assertThat(client.secretBackend()).isEmpty();
        }
    }
}
