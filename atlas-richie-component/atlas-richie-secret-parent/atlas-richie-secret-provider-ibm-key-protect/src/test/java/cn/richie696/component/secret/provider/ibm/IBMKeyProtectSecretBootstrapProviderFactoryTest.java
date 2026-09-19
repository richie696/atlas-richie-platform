package cn.richie696.component.secret.provider.ibm;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.provider.common.RemoteSecretProviderClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class IBMKeyProtectSecretBootstrapProviderFactoryTest {
    @Test
    void advertisesOnlyKeyProtectCapabilities() {
        IBMKeyProtectSecretBootstrapProviderFactory factory = new IBMKeyProtectSecretBootstrapProviderFactory();

        assertThat(factory.providerType()).isEqualTo("ibm-key-protect");
        assertThat(factory.capabilities()).containsExactlyInAnyOrder(
                SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP);
    }

    @Test
    void createsKmsOnlyClientFromBearerTokenConfiguration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.ibm-key-protect.endpoint", "https://ibm.example")
                .withProperty("platform.component.secret.ibm-key-protect.tenant-id", "instance-id")
                .withProperty("platform.component.secret.ibm-key-protect.authentication.type", "bearer-token")
                .withProperty("platform.component.secret.ibm-key-protect.authentication.token", "token");
        var context = new SecretBootstrapContext(environment, getClass().getClassLoader(),
                "ibm-key-protect", "ibm-key-protect", "platform.component.secret.ibm-key-protect");

        try (var client = (RemoteSecretProviderClient) new IBMKeyProtectSecretBootstrapProviderFactory().create(
                new BootstrapSecretProperties(), context)) {
            assertThat(client.descriptor().providerType()).isEqualTo("ibm-key-protect");
            assertThat(client.secretBackend()).isEmpty();
        }
    }
}
