/* Copyright (c) 2026 Richie (https://www.github.com/richie696) */
package cn.richie696.component.secret.provider.aliyun;

import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AliyunSecretConfigurationResolverTest {
    @Test
    void resolvesDefaultChainConfigurationWithoutCredentialMaterial() {
        var resolved = new AliyunSecretConfigurationResolver().resolve(
                baseEnvironment().withProperty(
                        "platform.component.secret.aliyun.kms.key-bindings.default-envelope", "alias/orders"),
                new BootstrapSecretProperties());

        assertThat(resolved.providerId()).isEqualTo("aliyun");
        assertThat(resolved.properties().getRegion()).isEqualTo("cn-hangzhou");
        assertThat(resolved.properties().getKms().getKeyBindings()).containsEntry("default-envelope", "alias/orders");
        assertThat(resolved.configurationHash()).hasSize(64);
    }

    @Test
    void resolvesNamedProviderFromFinalApplicationConfiguration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.providers.aliyun-primary.region", "cn-shanghai")
                .withProperty("platform.component.secret.providers.aliyun-primary.endpoint", "https://kms.cn-shanghai.aliyuncs.com");
        BootstrapSecretProperties bootstrap = new BootstrapSecretProperties();
        bootstrap.setActiveProvider("aliyun-primary");
        BootstrapSecretProperties.Provider provider = new BootstrapSecretProperties.Provider();
        provider.setType("aliyun");
        bootstrap.setProviders(Map.of("aliyun-primary", provider));

        var resolved = new AliyunSecretConfigurationResolver().resolve(environment, bootstrap);

        assertThat(resolved.providerId()).isEqualTo("aliyun-primary");
        assertThat(resolved.properties().getRegion()).isEqualTo("cn-shanghai");
    }

    @Test
    void rejectsExternalPlainHttpEndpoint() {
        assertThatThrownBy(() -> new AliyunSecretConfigurationResolver().resolve(
                baseEnvironment().withProperty(
                        "platform.component.secret.aliyun.endpoint", "http://kms.internal"),
                new BootstrapSecretProperties()))
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void dedicatedGatewayRequiresCaFile() {
        assertThatThrownBy(() -> new AliyunSecretConfigurationResolver().resolve(
                baseEnvironment().withProperty(
                        "platform.component.secret.aliyun.endpoint",
                        "https://kst-test.cryptoservice.kms.aliyuncs.com"),
                new BootstrapSecretProperties()))
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("ca-file");
    }

    private MockEnvironment baseEnvironment() {
        return new MockEnvironment().withProperty("platform.component.secret.aliyun.region", "cn-hangzhou");
    }
}
