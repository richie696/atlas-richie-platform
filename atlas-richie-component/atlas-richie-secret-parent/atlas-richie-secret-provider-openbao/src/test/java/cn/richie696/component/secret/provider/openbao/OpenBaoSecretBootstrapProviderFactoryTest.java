package cn.richie696.component.secret.provider.openbao;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class OpenBaoSecretBootstrapProviderFactoryTest {

    @Test
    void advertisesTransitSigningOnlyAfterTheProviderImplementsIt() {
        SecretBootstrapProviderFactory factory = ServiceLoader
                .load(SecretBootstrapProviderFactory.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(candidate -> "openbao".equals(candidate.providerType()))
                .findFirst()
                .orElseThrow();

        assertThat(factory.capabilities()).contains(SecretCapability.SIGN, SecretCapability.VERIFY);
    }
}
