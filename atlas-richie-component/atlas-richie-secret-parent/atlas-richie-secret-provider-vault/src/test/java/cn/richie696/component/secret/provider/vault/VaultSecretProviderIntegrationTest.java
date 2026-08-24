/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.vault;

import cn.richie696.component.secret.api.SecretOperations;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import cn.richie696.component.secret.core.DefaultSecretOperations;
import cn.richie696.component.secret.core.DefaultSecretResolver;
import cn.richie696.component.secret.core.crypto.ArseEnvelopeCodec;
import cn.richie696.component.secret.core.crypto.DefaultEnvelopeCrypto;
import cn.richie696.component.secret.core.crypto.DefaultSecretCipher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@EnabledIf("cn.richie696.component.secret.provider.vault.VaultIntegrationTestSupport#isEnabled")
class VaultSecretProviderIntegrationTest {

    @Test
    void loadsKvV2AndCompletesEnvelopeRoundTripAgainstRealVault() {
        String endpoint = VaultIntegrationTestSupport.endpoint();
        String token = VaultIntegrationTestSupport.token();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.vault.endpoint", endpoint)
                .withProperty("platform.component.secret.vault.authentication.type", "token")
                .withProperty("platform.component.secret.vault.authentication.token", token)
                .withProperty("platform.component.secret.vault.kv.mount", "kv")
                .withProperty("platform.component.secret.vault.transit.mount", "transit")
                .withProperty("platform.component.secret.vault.secrets.database-password.path", "runtime/database")
                .withProperty("platform.component.secret.vault.secrets.database-password.field", "password")
                .withProperty(
                        "platform.component.secret.vault.transit.key-bindings.default-envelope",
                        "orders-envelope")
                .withProperty(
                        "platform.component.secret.vault.transit.key-bindings.mfa.totp.data-key",
                        "mfa-secret-key");
        BootstrapSecretProperties properties = new BootstrapSecretProperties();
        properties.setEnabled(true);
        properties.getPropertySource().setApplication("orders");
        properties.getPropertySource().setEnvironment("e2e");
        properties.getResilience().setMaxAttempts(1);

        try (VaultSecretClient client = (VaultSecretClient) new VaultSecretBootstrapProviderFactory().create(
                properties,
                new SecretBootstrapContext(environment, getClass().getClassLoader()))) {
            var snapshot = client.load(new SecretBootstrapRequest(
                    "orders",
                    "e2e",
                    List.of(
                            "atlas-richie/e2e/orders/common",
                            "atlas-richie/e2e/orders/components"),
                    List.of()));
            assertThat(snapshot.values())
                    .containsEntry("platform.component.oauth.client-secret", "oauth-from-vault")
                    .containsEntry("platform.component.storage.access-key-secret", "storage-from-vault");

            SecretOperations operations = new DefaultSecretOperations(
                    new DefaultSecretResolver(client),
                    new DefaultSecretCipher(
                            new DefaultEnvelopeCrypto(client),
                            new ArseEnvelopeCodec(),
                            cn.richie696.component.secret.api.crypto.CryptoContext::empty));
            String databasePassword = operations.read(
                    "database-password",
                    chars -> new String(chars));
            assertThat(databasePassword).isEqualTo("database-from-vault");

            byte[] plaintext = "real-vault-envelope-round-trip".getBytes(StandardCharsets.UTF_8);
            try {
                String ciphertext = operations.encrypt("default-envelope", plaintext);
                assertThat(ciphertext).startsWith("arse:v1:");
                String decrypted = operations.decrypt(ciphertext, chars -> new String(chars));
                assertThat(decrypted).isEqualTo("real-vault-envelope-round-trip");
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
        }
    }

}
