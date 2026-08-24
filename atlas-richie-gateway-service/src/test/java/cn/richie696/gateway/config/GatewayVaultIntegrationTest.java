/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.gateway.config;

import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.SecretBootstrapTestHarness;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import cn.richie696.component.secret.provider.vault.VaultSecretBootstrapProviderFactory;
import cn.richie696.component.secret.provider.vault.VaultSecretClient;
import cn.richie696.testing.env.TestEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 Gateway 的受控 signing key 可以由业务系统选择的 Vault Provider 注入。 */
@Tag("integration")
@EnabledIf("cn.richie696.gateway.config.GatewayVaultIntegrationTest#isEnabled")
class GatewayVaultIntegrationTest {

    static boolean isEnabled() {
        return TestEnv.isTruthy("ATLAS_SECRET_VAULT_E2E", "atlas.secret.vault.e2e")
                && token() != null;
    }

    @Test
    void vaultSnapshotFeedsGatewayAuthenticationConfig() {
        BootstrapSecretProperties properties = new BootstrapSecretProperties();
        properties.setEnabled(true);
        properties.getPropertySource().setApplication("orders");
        properties.getPropertySource().setEnvironment("e2e");
        properties.getResilience().setMaxAttempts(1);

        MockEnvironment providerEnvironment = new MockEnvironment()
                .withProperty("platform.component.secret.vault.endpoint", endpoint())
                .withProperty("platform.component.secret.vault.authentication.type", "token")
                .withProperty("platform.component.secret.vault.authentication.token", token())
                .withProperty("platform.component.secret.vault.kv.mount", "kv")
                .withProperty("platform.component.secret.vault.transit.mount", "transit");
        try (VaultSecretClient client = (VaultSecretClient) new VaultSecretBootstrapProviderFactory().create(
                properties,
                new SecretBootstrapContext(providerEnvironment, getClass().getClassLoader()))) {
            var snapshot = client.load(new SecretBootstrapRequest(
                    "orders", "e2e",
                    List.of("atlas-richie/e2e/orders/common", "atlas-richie/e2e/orders/components"),
                    List.of()));
            var environment = SecretBootstrapTestHarness.apply(
                    getClass().getClassLoader(),
                    Map.of(
                            "spring.application.name", "orders",
                            "platform.component.secret.enabled", true,
                            "platform.component.secret.strict-mode", true,
                            "platform.gateway.security.authentication.secret-key", "local-gateway-key"),
                    snapshot.values());

            assertThat(Binder.get(environment)
                    .bind("platform.gateway.security.authentication", AuthenticationConfig.class)
                    .get().getSecretKey()).isEqualTo("gateway-from-vault");
        }
    }

    private static String endpoint() {
        return TestEnv.firstResolved(
                new String[]{"ATLAS_SECRET_VAULT_ENDPOINT"},
                new String[]{"atlas.secret.vault.endpoint"},
                "http://127.0.0.1:8200");
    }

    private static String token() {
        return TestEnv.firstResolved(
                new String[]{"ATLAS_SECRET_VAULT_TOKEN"},
                new String[]{"atlas.secret.vault.token"});
    }
}
