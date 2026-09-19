package cn.richie696.component.secret.provider.oci;

import cn.richie696.component.secret.api.SecretCapability;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OciSecretBootstrapProviderFactoryTest {
    @Test
    void advertisesVaultAndKmsCapabilities() {
        OciSecretBootstrapProviderFactory factory = new OciSecretBootstrapProviderFactory();

        assertThat(factory.providerType()).isEqualTo("oci");
        assertThat(factory.capabilities()).containsExactlyInAnyOrder(
                SecretCapability.SECRET_READ,
                SecretCapability.SECRET_VERSIONING,
                SecretCapability.KEY_WRAP,
                SecretCapability.KEY_UNWRAP);
    }
}
