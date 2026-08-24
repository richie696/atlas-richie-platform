/* Copyright (c) 2026 Richie (https://www.github.com/richie696) */
package cn.richie696.component.secret.provider.aliyun;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class AliyunSecretBootstrapProviderFactoryTest {
    @Test
    void serviceLoaderAdvertisesOnlyImplementedCapabilities() {
        SecretBootstrapProviderFactory factory = ServiceLoader.load(SecretBootstrapProviderFactory.class)
                .stream().map(ServiceLoader.Provider::get)
                .filter(candidate -> "aliyun".equals(candidate.providerType()))
                .findFirst().orElseThrow();

        assertThat(factory.capabilities()).containsExactlyInAnyOrder(
                SecretCapability.SECRET_READ, SecretCapability.SECRET_VERSIONING,
                SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP);
        assertThat(factory.capabilities()).doesNotContain(SecretCapability.DIRECT_ENCRYPT, SecretCapability.REWRAP);
    }
}
