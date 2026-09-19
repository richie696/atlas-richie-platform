/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api;

import cn.richie696.component.secret.api.crypto.CipherEnvelope;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyPurpose;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.WrappedKey;
import cn.richie696.component.secret.api.exception.SecretBootstrapException;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.api.exception.SecretException;
import cn.richie696.component.secret.api.exception.SecretIntegrityException;
import cn.richie696.component.secret.api.provider.SecretProviderConfiguration;
import cn.richie696.component.secret.api.provider.SecretProviderDescriptor;
import cn.richie696.component.secret.api.provider.SecretProviderSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretApiContractTest {

    @Test
    void valueObjectsNormalizeInputsAndProtectMutableState() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        SecretMetadata metadata = new SecretMetadata("v1", now, null, null);
        assertThat(metadata.attributes()).isEmpty();
        assertThat(new SecretVersion("v1", now).value()).isEqualTo("v1");

        SecretVersionSelector latest = SecretVersionSelector.latest();
        assertThat(latest.type()).isEqualTo(SecretVersionSelector.Type.LATEST);
        assertThat(latest.value()).isNull();
        assertThat(SecretVersionSelector.version("v1").value()).isEqualTo("v1");
        assertThat(SecretVersionSelector.stage("AWSCURRENT").type())
                .isEqualTo(SecretVersionSelector.Type.STAGE);
        assertThat(SecretVersionSelector.alias("current").type())
                .isEqualTo(SecretVersionSelector.Type.ALIAS);

        SecretReference reference = SecretReference.latestField("db.password", "password");
        assertThat(reference.logicalName()).isEqualTo("db.password");
        assertThat(reference.field()).isEqualTo("password");
        assertThat(SecretReference.latest("db.password").field()).isNull();

        SecretSnapshotChangedEvent event = new SecretSnapshotChangedEvent(null, null, "aws", "v2", null);
        assertThat(event.changedAt()).isNotNull();

        CryptoContext context = new CryptoContext(new byte[]{1, 2}, Map.of("tenant", "demo"));
        byte[] aad = context.associatedData();
        aad[0] = 9;
        assertThat(context.associatedData()).containsExactly(1, 2);
        assertThat(context.attributes()).containsEntry("tenant", "demo");
        assertThat(context).isEqualTo(new CryptoContext(new byte[]{1, 2}, Map.of("tenant", "demo")));
        assertThat(context.toString()).contains("PROTECTED");
        assertThat(CryptoContext.empty().associatedData()).isEmpty();
    }

    @Test
    void cryptoObjectsValidateAndReturnDefensiveCopies() {
        KeyReference key = KeyReference.envelopeEncryption("data-kek");
        WrappedKey wrapped = new WrappedKey(new byte[]{3, 4}, "AES-KW");
        byte[] wrappedValue = wrapped.value();
        wrappedValue[0] = 8;
        assertThat(wrapped.value()).containsExactly(3, 4);
        assertThat(wrapped.algorithm()).isEqualTo("AES-KW");
        assertThat(wrapped.toString()).contains("PROTECTED");

        CipherEnvelope envelope = new CipherEnvelope(
                1, "AES/GCM", key, wrapped, new byte[]{5, 6}, new byte[]{7, 8});
        byte[] nonce = envelope.nonce();
        nonce[0] = 0;
        assertThat(envelope.nonce()).containsExactly(5, 6);
        assertThat(envelope.ciphertext()).containsExactly(7, 8);
        assertThat(envelope.version()).isEqualTo(1);
        assertThat(envelope.algorithm()).isEqualTo("AES/GCM");
        assertThat(envelope.keyReference()).isEqualTo(key);
        assertThat(envelope.wrappedKey()).isEqualTo(wrapped);
        assertThat(envelope.toString()).contains("ciphertext=[PROTECTED]");

        assertThatThrownBy(() -> new WrappedKey(new byte[0], "AES"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WrappedKey(new byte[]{1}, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CipherEnvelope(0, "AES", key, wrapped, new byte[]{1}, new byte[]{2}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CipherEnvelope(1, " ", key, wrapped, new byte[]{1}, new byte[]{2}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CipherEnvelope(1, "AES", key, wrapped, new byte[0], new byte[]{2}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void providerContractsProtectConfigurationAndExposeCapabilities() {
        SecretProviderConfiguration configuration = new SecretProviderConfiguration(
                "aws", Map.of("region", "cn-test-1", "binary", new byte[]{1, 2}));
        assertThat(configuration.properties()).containsEntry("region", "cn-test-1");
        byte[] binary = (byte[]) configuration.properties().get("binary");
        binary[0] = 9;
        assertThat((byte[]) configuration.properties().get("binary")).containsExactly(1, 2);
        assertThat(configuration.toString()).contains("PROTECTED");

        SecretProviderDescriptor descriptor = new SecretProviderDescriptor(
                "aws-secrets-manager", "primary", Set.of(SecretCapability.SECRET_READ, SecretCapability.SECRET_WRITE));
        assertThat(descriptor.capabilities()).contains(SecretCapability.SECRET_READ, SecretCapability.SECRET_WRITE);

        SecretProviderSession session = new SecretProviderSession() {
            @Override
            public SecretProviderDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public Optional<SecretBackend> secretBackend() {
                return Optional.empty();
            }

            @Override
            public Optional<cn.richie696.component.secret.api.crypto.KeyWrappingBackend> keyWrappingBackend() {
                return Optional.empty();
            }

            @Override
            public void close() {
            }
        };
        assertThat(session.descriptor()).isEqualTo(descriptor);
        assertThat(session.signingBackend()).isEmpty();
        session.close();
    }

    @Test
    void defaultFacadeMethodsAndExceptionMetadataAreStable() {
        SecretResolver resolver = new SecretResolver() {
            @Override
            public SecretValue resolve(SecretReference reference) {
                return null;
            }

            @Override
            public SecretMetadata metadata(SecretReference reference) {
                return new SecretMetadata("v1", Instant.EPOCH, null, Map.of());
            }
        };
        assertThat(resolver.resolve("db.password")).isNull();
        assertThat(resolver.metadata("db.password").version()).isEqualTo("v1");

        SecretOperations operations = new SecretOperations() {
            @Override
            public <T> T read(String logicalName, SecretCallback<T> callback) {
                return callback.apply(null);
            }

            @Override
            public String encrypt(String logicalKey, byte[] plaintext) {
                return "ciphertext";
            }

            @Override
            public <T> T decrypt(String ciphertext, SecretCallback<T> callback) {
                return callback.apply(null);
            }
        };
        assertThatThrownBy(() -> operations.sign("key", new byte[]{1}))
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("does not support signing");
        assertThatThrownBy(() -> operations.verify("key", new byte[]{1}, null))
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("signature verification");

        Throwable cause = new IllegalStateException("cause");
        assertThat(new SecretException("SEC-001", "message", cause).errorCode()).isEqualTo("SEC-001");
        assertThat(new SecretBootstrapException("SEC-002", "message", cause).getCause()).isEqualTo(cause);
        assertThat(new SecretConfigurationException("SEC-003", "message").errorCode()).isEqualTo("SEC-003");
        assertThat(new SecretCryptoException("SEC-004", "message").errorCode()).isEqualTo("SEC-004");
        assertThat(new SecretIntegrityException("message").errorCode()).isEqualTo("SEC-CRYPTO-003");
        assertThat(new SecretIntegrityException("message", cause).getCause()).isEqualTo(cause);
    }

    @Test
    void publicValidationRejectsUnsafeReferences() {
        assertThatThrownBy(() -> SecretVersionSelector.version(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecretVersionSelector(null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SecretReference.latest("../secret"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SecretReference.latestField("name", "/field"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecretSnapshotChangedEvent(null, null, " ", "v1", Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KeyReference.envelopeEncryption("/physical/path"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KeyReference.envelopeEncryption("key", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
