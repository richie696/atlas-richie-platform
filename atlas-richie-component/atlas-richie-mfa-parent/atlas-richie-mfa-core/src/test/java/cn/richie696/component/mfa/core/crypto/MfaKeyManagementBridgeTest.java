/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.mfa.core.crypto;

import cn.richie696.component.secret.api.SecretCallback;
import cn.richie696.component.secret.api.SecretOperations;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MfaKeyManagementBridgeTest {

    @Test
    void hidesProviderDetailsAndUsesOneStableLogicalKey() {
        RecordingSecretOperations operations = new RecordingSecretOperations();
        MfaKeyManagementBridge bridge = new MfaKeyManagementBridge(operations);

        String envelope = bridge.storeSecret("tenant-a", "user-a", "JBSWY3DPEHPK3PXP");

        assertThat(envelope).isEqualTo("arse:v1:test-envelope");
        assertThat(operations.logicalKey).isEqualTo("mfa.totp.data-key");
        assertThat(operations.capturedPlaintext).containsOnly((byte) 0);
        assertThat(bridge.retrieveSecret(envelope)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void rejectsUnknownLegacyReferencesWhenUnifiedSecretIsActive() {
        MfaKeyManagementBridge bridge = new MfaKeyManagementBridge(new RecordingSecretOperations());

        assertThatThrownBy(() -> bridge.retrieveSecret("mfa/tenant-a/user-a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("arse:v1");
    }

    private static final class RecordingSecretOperations implements SecretOperations {
        private String logicalKey;
        private byte[] capturedPlaintext;

        @Override
        public <T> T read(String logicalName, SecretCallback<T> callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String encrypt(String logicalKey, byte[] plaintext) {
            this.logicalKey = logicalKey;
            this.capturedPlaintext = plaintext;
            return "arse:v1:test-envelope";
        }

        @Override
        public <T> T decrypt(String ciphertext, SecretCallback<T> callback) {
            return callback.apply("JBSWY3DPEHPK3PXP".getBytes(StandardCharsets.UTF_8));
        }
    }
}
