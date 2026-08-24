/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.vault;

import cn.richie696.testing.env.TestEnv;

/**
 * Vault 外部实例集测支撑：只从环境读取连接信息，避免把 token 固化到测试代码。
 */
final class VaultIntegrationTestSupport {

    private VaultIntegrationTestSupport() {
    }

    static boolean isEnabled() {
        return TestEnv.isTruthy("ATLAS_SECRET_VAULT_E2E", "atlas.secret.vault.e2e")
                && endpoint() != null
                && token() != null;
    }

    static String endpoint() {
        return TestEnv.firstResolved(
                new String[]{"ATLAS_SECRET_VAULT_ENDPOINT"},
                new String[]{"atlas.secret.vault.endpoint"},
                "http://127.0.0.1:8200");
    }

    static String token() {
        return TestEnv.firstResolved(
                new String[]{"ATLAS_SECRET_VAULT_TOKEN"},
                new String[]{"atlas.secret.vault.token"});
    }

    static String approleRoleId() {
        return TestEnv.firstResolved(
                new String[]{"ATLAS_SECRET_VAULT_APPROLE_ROLE_ID"},
                new String[]{"atlas.secret.vault.approle.role-id"});
    }

    static String approleSecretId() {
        return TestEnv.firstResolved(
                new String[]{"ATLAS_SECRET_VAULT_APPROLE_SECRET_ID"},
                new String[]{"atlas.secret.vault.approle.secret-id"});
    }

    static String agentEndpoint() {
        return TestEnv.firstResolved(
                new String[]{"ATLAS_SECRET_VAULT_AGENT_ENDPOINT"},
                new String[]{"atlas.secret.vault.agent.endpoint"});
    }

    static String kubernetesTokenFile() {
        return TestEnv.firstResolved(
                new String[]{"ATLAS_SECRET_VAULT_KUBERNETES_TOKEN_FILE"},
                new String[]{"atlas.secret.vault.kubernetes.token-file"});
    }

    static String kubernetesRole() {
        return TestEnv.firstResolved(
                new String[]{"ATLAS_SECRET_VAULT_KUBERNETES_ROLE"},
                new String[]{"atlas.secret.vault.kubernetes.role"});
    }

    static String kubernetesPath() {
        return TestEnv.firstResolved(
                new String[]{"ATLAS_SECRET_VAULT_KUBERNETES_PATH"},
                new String[]{"atlas.secret.vault.kubernetes.path"},
                "kubernetes");
    }

    static String deniedToken() {
        return TestEnv.firstResolved(
                new String[]{"ATLAS_SECRET_VAULT_DENIED_TOKEN"},
                new String[]{"atlas.secret.vault.denied-token"});
    }

    static String signingKey() {
        return TestEnv.firstResolved(
                new String[]{"ATLAS_SECRET_VAULT_SIGNING_KEY"},
                new String[]{"atlas.secret.vault.signing-key"});
    }

}
