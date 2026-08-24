/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.core;

import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyPurpose;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.SignatureValue;
import cn.richie696.component.secret.api.crypto.SigningBackend;
import cn.richie696.component.secret.api.crypto.SigningService;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.core.crypto.DefaultSigningService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SigningServiceTest {

    @Test
    void facadeMapsLogicalKeyToSigningPurposeAndKeepsProviderDetailsInternal() {
        AtomicReference<KeyReference> reference = new AtomicReference<>();
        SigningBackend backend = new SigningBackend() {
            @Override
            public SignatureValue sign(KeyReference key, byte[] payload, CryptoContext context) {
                reference.set(key);
                return new SignatureValue("opaque-signature");
            }

            @Override
            public boolean verify(KeyReference key, byte[] payload, SignatureValue signature,
                                  CryptoContext context) {
                reference.set(key);
                return "opaque-signature".equals(signature.value());
            }
        };

        SigningService service = new DefaultSigningService(backend);
        SignatureValue signature = service.sign("oauth.signing", "payload".getBytes(StandardCharsets.UTF_8));

        assertThat(signature.value()).isEqualTo("opaque-signature");
        assertThat(reference.get().logicalKey()).isEqualTo("oauth.signing");
        assertThat(reference.get().purpose()).isEqualTo(KeyPurpose.SIGNING);
        assertThat(service.verify("oauth.signing", "payload".getBytes(StandardCharsets.UTF_8), signature))
                .isTrue();
    }

    @Test
    void rejectsEmptyPayloadBeforeProviderCall() {
        SigningService service = new DefaultSigningService(new SigningBackend() {
            @Override
            public SignatureValue sign(KeyReference key, byte[] payload, CryptoContext context) {
                throw new AssertionError("provider must not be called");
            }

            @Override
            public boolean verify(KeyReference key, byte[] payload, SignatureValue signature,
                                  CryptoContext context) {
                throw new AssertionError("provider must not be called");
            }
        });

        assertThatThrownBy(() -> service.sign("oauth.signing", new byte[0]))
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("payload");
    }
}
