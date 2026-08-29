/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.starter;

import cn.richie696.component.secret.api.SecretBackend;
import cn.richie696.component.secret.api.SecretMetadata;
import cn.richie696.component.secret.api.SecretReference;
import cn.richie696.component.secret.api.SecretValue;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.KeyWrappingBackend;
import cn.richie696.component.secret.api.crypto.SignatureValue;
import cn.richie696.component.secret.api.crypto.SigningBackend;
import cn.richie696.component.secret.api.crypto.WrappedKey;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.api.provider.SecretProviderSession;
import cn.richie696.component.secret.bootstrap.SecretBootstrapState;
import cn.richie696.component.secret.bootstrap.SecretProviderTopology;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;

/**
 * 内部能力路由器。业务代码只注入 SecretOperations，不需要了解 Provider ID、
 * KMS 类型或路由规则。
 */
public final class SecretProviderRouter implements AutoCloseable {

    private final SecretProviderTopology topology;

    public SecretProviderRouter(SecretBootstrapState state) {
        if (state == null || state.topology() == null) {
            throw new SecretConfigurationException(
                    "SEC-BOOT-002", "Secret Provider topology is unavailable");
        }
        this.topology = state.topology();
    }

    public SecretValue read(SecretReference reference) {
        return requiredSecretBackend().read(reference);
    }

    public SecretMetadata metadata(SecretReference reference) {
        return requiredSecretBackend().metadata(reference);
    }

    public WrappedKey wrap(KeyReference keyReference, byte[] plaintextKey, CryptoContext context) {
        return requiredWrappingBackend().wrap(keyReference, plaintextKey, context);
    }

    public byte[] unwrap(KeyReference keyReference, WrappedKey wrappedKey, CryptoContext context) {
        return requiredWrappingBackend().unwrap(keyReference, wrappedKey, context);
    }

    public SignatureValue sign(KeyReference keyReference, byte[] payload, CryptoContext context) {
        return requiredSigningBackend().sign(keyReference, payload, context);
    }

    public boolean verify(
            KeyReference keyReference,
            byte[] payload,
            SignatureValue signature,
            CryptoContext context) {
        return requiredSigningBackend().verify(keyReference, payload, signature, context);
    }

    public java.util.Optional<SecretBackend> secretBackend() {
        if (!topology.supportsRoute(SecretProviderTopology.SECRET_READ)) return java.util.Optional.empty();
        String providerId = topology.providerId(SecretProviderTopology.SECRET_READ);
        return session(providerId).secretBackend();
    }

    public java.util.Optional<KeyWrappingBackend> keyWrappingBackend() {
        if (!topology.supportsRoute(SecretProviderTopology.ENVELOPE_CRYPTO)) return java.util.Optional.empty();
        String providerId = topology.providerId(SecretProviderTopology.ENVELOPE_CRYPTO);
        return session(providerId).keyWrappingBackend();
    }

    public java.util.Optional<SigningBackend> signingBackend() {
        if (!topology.supportsRoute(SecretProviderTopology.SIGNING)) return java.util.Optional.empty();
        String providerId = topology.providerId(SecretProviderTopology.SIGNING);
        return session(providerId).signingBackend();
    }

    private SecretBackend requiredSecretBackend() {
        return secretBackend().orElseThrow(() -> missingCapability("routed Provider", "SECRET_READ"));
    }

    private KeyWrappingBackend requiredWrappingBackend() {
        return keyWrappingBackend().orElseThrow(() -> missingCapability(
                "routed Provider", "KEY_WRAP/KEY_UNWRAP"));
    }

    private SigningBackend requiredSigningBackend() {
        return signingBackend().orElseThrow(() -> missingCapability("routed Provider", "SIGN/VERIFY"));
    }

    private SecretProviderSession session(String providerId) {
        SecretBootstrapClient client = topology.client(providerId);
        if (client instanceof SecretProviderSession session) {
            return session;
        }
        throw new SecretConfigurationException(
                "SEC-CAP-001",
                "Secret Provider '" + providerId + "' has no runtime session");
    }

    private SecretConfigurationException missingCapability(String providerId, String capability) {
        return new SecretConfigurationException(
                "SEC-CAP-001",
                "Secret Provider '" + providerId + "' does not support " + capability);
    }

    @Override
    public void close() {
        topology.close();
    }
}
