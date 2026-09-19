package cn.richie696.component.secret.provider.baidu;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.provider.common.RemoteSecretProviderClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class BaiduSecretBootstrapProviderFactoryTest {
    @Test
    void advertisesOnlyKmsCapabilities() {
        BaiduSecretBootstrapProviderFactory factory = new BaiduSecretBootstrapProviderFactory();

        assertThat(factory.providerType()).isEqualTo("baidu");
        assertThat(factory.capabilities()).containsExactlyInAnyOrder(
                SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP);
    }

    @Test
    void createsKmsOnlyClientFromAccessKeyConfiguration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.baidu.endpoint", "https://baidu.example")
                .withProperty("platform.component.secret.baidu.authentication.type", "access-key")
                .withProperty("platform.component.secret.baidu.authentication.access-key-id", "ak")
                .withProperty("platform.component.secret.baidu.authentication.access-key-secret", "sk");
        var context = new SecretBootstrapContext(environment, getClass().getClassLoader(),
                "baidu", "baidu", "platform.component.secret.baidu");

        try (var client = (RemoteSecretProviderClient) new BaiduSecretBootstrapProviderFactory().create(
                new BootstrapSecretProperties(), context)) {
            assertThat(client.descriptor().providerType()).isEqualTo("baidu");
            assertThat(client.secretBackend()).isEmpty();
        }
    }
}
