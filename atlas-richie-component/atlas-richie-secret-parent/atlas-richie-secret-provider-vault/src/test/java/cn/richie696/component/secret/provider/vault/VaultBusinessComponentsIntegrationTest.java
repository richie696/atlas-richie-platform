/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.vault;

import cn.richie696.component.oauth.core.config.OAuth2Properties;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.core.support.HmacAccessTokenSigner;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.testkit.SecretBootstrapTestHarness;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import cn.richie696.component.secret.core.DefaultSecretOperations;
import cn.richie696.component.secret.core.DefaultSecretResolver;
import cn.richie696.component.secret.core.crypto.ArseEnvelopeCodec;
import cn.richie696.component.secret.core.crypto.DefaultEnvelopeCrypto;
import cn.richie696.component.secret.core.crypto.DefaultSecretCipher;
import cn.richie696.component.storage.config.StorageProperties;
import cn.richie696.component.ai.config.AiModelProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vault -> Secret Bootstrap -> 业务组件属性/桥接层的真实链路集成测试。
 * <p>不调用云厂商 API；它验证业务系统只配置 Secret Provider 后，MFA、OAuth、Storage、AI
 * 能从同一个 Vault 快照得到自己的受控配置。各业务组件的远端服务协议仍由其专用 IT 覆盖。</p>
 */
@Tag("integration")
@EnabledIf("cn.richie696.component.secret.provider.vault.VaultIntegrationTestSupport#isEnabled")
class VaultBusinessComponentsIntegrationTest {

    @Test
    void vaultSnapshotFeedsMfaOauthStorageAndAiWithoutProviderKnowledge() throws Exception {
        BootstrapSecretProperties properties = new BootstrapSecretProperties();
        properties.setEnabled(true);
        properties.getPropertySource().setApplication("orders");
        properties.getPropertySource().setEnvironment("e2e");
        properties.getResilience().setMaxAttempts(1);

        MockEnvironment providerEnvironment = new MockEnvironment();
        providerEnvironment
                .withProperty("platform.component.secret.vault.endpoint", VaultIntegrationTestSupport.endpoint())
                .withProperty("platform.component.secret.vault.authentication.type", "token")
                .withProperty("platform.component.secret.vault.authentication.token", VaultIntegrationTestSupport.token())
                .withProperty("platform.component.secret.vault.kv.mount", "kv")
                .withProperty("platform.component.secret.vault.transit.mount", "transit")
                .withProperty("platform.component.secret.vault.transit.key-bindings.mfa.totp.data-key", "mfa-secret-key");

        try (VaultSecretClient client = (VaultSecretClient) new VaultSecretBootstrapProviderFactory().create(
                properties,
                new SecretBootstrapContext(providerEnvironment, getClass().getClassLoader()))) {
            var snapshot = client.load(new SecretBootstrapRequest(
                    "orders",
                    "e2e",
                    List.of(
                            "atlas-richie/e2e/orders/common",
                            "atlas-richie/e2e/orders/components"),
                    List.of()));

            Map<String, Object> local = Map.of(
                    "spring.application.name", "orders",
                    "platform.component.secret.enabled", true,
                    "platform.component.secret.strict-mode", true,
                    "platform.component.oauth.enabled", true,
                    "platform.component.oauth.token-secret", "local-oauth-secret",
                    "platform.component.storage.object.access-key-id", "local-storage-id",
                    "platform.component.storage.object.access-key-secret", "local-storage-secret",
                    "platform.component.ai.chat.product-search.api-keys[0]", "local-ai-key");
            ConfigurableEnvironment environment = SecretBootstrapTestHarness.apply(
                    getClass().getClassLoader(), local, snapshot.values());

            OAuth2Properties oauth = Binder.get(environment)
                    .bind("platform.component.oauth", OAuth2Properties.class).get();
            assertThat(oauth.getTokenSecret()).isEqualTo("oauth-from-vault");
            HmacAccessTokenSigner signer = new HmacAccessTokenSigner(oauth);
            String token = signer.sign(
                    "orders-client",
                    ClientConfig.builder().tokenValidDuration(1).build(),
                    List.of("orders.read"),
                    "orders-api");
            assertThat(signer.verify(token).clientId()).isEqualTo("orders-client");
            assertThat(Binder.get(environment).bind("platform.component.storage", StorageProperties.class)
                    .get().getObject().getAccessKeySecret()).isEqualTo("storage-from-vault");
            assertThat(Binder.get(environment).bind("platform.component.ai", AiModelProperties.class)
                    .get().getChat().get("product-search").getApiKeys())
                    .containsExactly("ai-from-vault");

            DefaultSecretOperations operations = new DefaultSecretOperations(
                    new DefaultSecretResolver(client),
                    new DefaultSecretCipher(
                            new DefaultEnvelopeCrypto(client),
                            new ArseEnvelopeCodec(),
                            cn.richie696.component.secret.api.crypto.CryptoContext::empty));
            Class<?> bridgeType = Class.forName("cn.richie696.component.mfa.core.crypto.MfaKeyManagementBridge");
            Object bridge = bridgeType.getConstructor(cn.richie696.component.secret.api.SecretOperations.class)
                    .newInstance(operations);
            String stored = (String) bridgeType
                    .getMethod("storeSecret", String.class, String.class, String.class)
                    .invoke(bridge, "tenant-a", "user-a", "JBSWY3DPEHPK3PXP");
            assertThat(stored).startsWith("arse:v1:");
            assertThat(bridgeType.getMethod("retrieveSecret", String.class).invoke(bridge, stored))
                    .isEqualTo("JBSWY3DPEHPK3PXP");
        }
    }
}
