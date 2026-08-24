/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.vault;

import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import cn.richie696.testing.env.TestEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Vault 认证模式真实链路测试。
 * <p>Token-file、AppRole、Agent 分别验证凭据文件、Vault 登录 API 和 Agent listener；
 * Kubernetes 认证只在显式提供真实 ServiceAccount JWT 与 Vault Kubernetes Auth 配置时执行。</p>
 */
@Tag("integration")
@EnabledIf("cn.richie696.component.secret.provider.vault.VaultAuthenticationIntegrationTest#isEnabled")
class VaultAuthenticationIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    static boolean isEnabled() {
        boolean standardAuthentication = VaultIntegrationTestSupport.token() != null;
        boolean kubernetesAuthentication = TestEnv.isTruthy(
                "ATLAS_SECRET_VAULT_KUBERNETES_E2E", "atlas.secret.vault.kubernetes.e2e")
                && VaultIntegrationTestSupport.kubernetesTokenFile() != null
                && VaultIntegrationTestSupport.kubernetesRole() != null;
        return TestEnv.isTruthy("ATLAS_SECRET_VAULT_E2E", "atlas.secret.vault.e2e")
                && (standardAuthentication || kubernetesAuthentication);
    }

    @Test
    void tokenFileAuthenticationReadsRealVault() throws Exception {
        assumeTrue(VaultIntegrationTestSupport.token() != null,
                "set ATLAS_SECRET_VAULT_TOKEN to enable Token File E2E");
        Path tokenFile = temporaryDirectory.resolve("vault-token");
        Files.writeString(tokenFile, VaultIntegrationTestSupport.token());
        try (VaultSecretClient client = create(
                VaultIntegrationTestSupport.endpoint(),
                "token-file",
                tokenFile.toString(),
                null,
                null)) {
            assertThat(load(client).values())
                    .containsEntry("platform.component.oauth.token-secret", "oauth-from-vault");
        }
    }

    @Test
    void appRoleAuthenticationReadsRealVault() {
        assumeTrue(VaultIntegrationTestSupport.approleRoleId() != null,
                "set ATLAS_SECRET_VAULT_APPROLE_ROLE_ID to enable AppRole E2E");
        assumeTrue(VaultIntegrationTestSupport.approleSecretId() != null,
                "set ATLAS_SECRET_VAULT_APPROLE_SECRET_ID to enable AppRole E2E");
        try (VaultSecretClient client = create(
                VaultIntegrationTestSupport.endpoint(),
                "approle",
                null,
                VaultIntegrationTestSupport.approleRoleId(),
                VaultIntegrationTestSupport.approleSecretId())) {
            assertThat(load(client).values())
                    .containsEntry("platform.component.oauth.token-secret", "oauth-from-vault");
        }
    }

    @Test
    void agentAuthenticationReadsThroughRealAgentListener() {
        assumeTrue(VaultIntegrationTestSupport.agentEndpoint() != null,
                "set ATLAS_SECRET_VAULT_AGENT_ENDPOINT to enable Agent E2E");
        try (VaultSecretClient client = create(
                VaultIntegrationTestSupport.agentEndpoint(),
                "agent",
                null,
                null,
                null)) {
            assertThat(load(client).values())
                    .containsEntry("platform.component.oauth.token-secret", "oauth-from-vault");
        }
    }

    @Test
    void kubernetesAuthenticationReadsWithRealServiceAccountJwt() {
        assumeTrue(TestEnv.isTruthy("ATLAS_SECRET_VAULT_KUBERNETES_E2E",
                        "atlas.secret.vault.kubernetes.e2e"),
                "set ATLAS_SECRET_VAULT_KUBERNETES_E2E=true to enable Kubernetes Auth E2E");
        assumeTrue(VaultIntegrationTestSupport.kubernetesTokenFile() != null,
                "set ATLAS_SECRET_VAULT_KUBERNETES_TOKEN_FILE to a real ServiceAccount JWT file");
        assumeTrue(VaultIntegrationTestSupport.kubernetesRole() != null,
                "set ATLAS_SECRET_VAULT_KUBERNETES_ROLE to the Vault Kubernetes role");
        try (VaultSecretClient client = create(
                VaultIntegrationTestSupport.endpoint(), "kubernetes", null, null, null)) {
            assertThat(load(client).values())
                    .containsEntry("platform.component.oauth.token-secret", "oauth-from-vault");
        }
    }

    private VaultSecretClient create(
            String endpoint,
            String authenticationType,
            String tokenFile,
            String roleId,
            String secretId) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.vault.endpoint", endpoint)
                .withProperty("platform.component.secret.vault.authentication.type", authenticationType)
                .withProperty("platform.component.secret.vault.kv.mount", "kv")
                .withProperty("platform.component.secret.vault.transit.mount", "transit");
        if (tokenFile != null) {
            environment.withProperty("platform.component.secret.vault.authentication.token-file", tokenFile);
        }
        if (roleId != null) {
            environment.withProperty("platform.component.secret.vault.authentication.role-id", roleId);
        }
        if (secretId != null) {
            environment.withProperty("platform.component.secret.vault.authentication.secret-id", secretId);
        }
        if ("kubernetes".equals(authenticationType)) {
            environment.withProperty("platform.component.secret.vault.authentication.role",
                    VaultIntegrationTestSupport.kubernetesRole());
            environment.withProperty("platform.component.secret.vault.authentication.kubernetes-path",
                    VaultIntegrationTestSupport.kubernetesPath());
            environment.withProperty("platform.component.secret.vault.authentication.service-account-token-file",
                    VaultIntegrationTestSupport.kubernetesTokenFile());
        }
        BootstrapSecretProperties properties = new BootstrapSecretProperties();
        properties.setEnabled(true);
        properties.getPropertySource().setApplication("orders");
        properties.getPropertySource().setEnvironment("e2e");
        properties.getResilience().setMaxAttempts(1);
        return (VaultSecretClient) new VaultSecretBootstrapProviderFactory().create(
                properties,
                new SecretBootstrapContext(environment, getClass().getClassLoader()));
    }

    private cn.richie696.component.secret.bootstrap.spi.SecretBootstrapResult load(VaultSecretClient client) {
        return client.load(new SecretBootstrapRequest(
                "orders",
                "e2e",
                List.of(
                        "atlas-richie/e2e/orders/common",
                        "atlas-richie/e2e/orders/components"),
                List.of()));
    }
}
