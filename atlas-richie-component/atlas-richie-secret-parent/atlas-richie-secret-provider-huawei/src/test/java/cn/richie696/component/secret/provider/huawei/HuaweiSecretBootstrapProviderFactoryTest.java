package cn.richie696.component.secret.provider.huawei;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.provider.common.RemoteSecretProviderClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class HuaweiSecretBootstrapProviderFactoryTest {
    @Test
    void advertisesCsmsAndDewKmsCapabilities() {
        HuaweiSecretBootstrapProviderFactory factory = new HuaweiSecretBootstrapProviderFactory();

        assertThat(factory.providerType()).isEqualTo("huawei");
        assertThat(factory.capabilities()).containsExactlyInAnyOrder(
                SecretCapability.SECRET_READ,
                SecretCapability.SECRET_VERSIONING,
                SecretCapability.KEY_WRAP,
                SecretCapability.KEY_UNWRAP);
    }

    @Test
    void createsSdkBackedClientFromAccessKeyConfiguration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.huawei.endpoint", "https://huaweicloud.example")
                .withProperty("platform.component.secret.huawei.project-id", "project")
                .withProperty("platform.component.secret.huawei.region", "cn-north-4")
                .withProperty("platform.component.secret.huawei.authentication.type", "access-key")
                .withProperty("platform.component.secret.huawei.authentication.access-key-id", "ak")
                .withProperty("platform.component.secret.huawei.authentication.access-key-secret", "sk");
        var context = new SecretBootstrapContext(environment, getClass().getClassLoader(),
                "huawei", "huawei", "platform.component.secret.huawei");

        try (var client = (RemoteSecretProviderClient) new HuaweiSecretBootstrapProviderFactory().create(
                new BootstrapSecretProperties(), context)) {
            assertThat(client.descriptor().providerType()).isEqualTo("huawei");
        }
    }
}
