/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api;

import cn.richie696.component.secret.api.crypto.KeyReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeastKnowledgeReferenceTest {

    @Test
    void projectReferencesContainLogicalKnowledgeOnly() {
        SecretReference secret = SecretReference.latest("storage.object.credential");
        KeyReference key = KeyReference.envelopeEncryption("mfa-data-kek");

        assertThat(secret.logicalName()).isEqualTo("storage.object.credential");
        assertThat(secret.version()).isEqualTo(SecretVersionSelector.latest());
        assertThat(key.logicalKey()).isEqualTo("mfa-data-kek");
        assertThat(key.version()).isEqualTo("current");
    }

    @Test
    void projectCannotSmuggleProviderEndpointsOrPhysicalPaths() {
        assertThatThrownBy(() -> SecretReference.latest("https://vault.example/secret/app"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KeyReference.envelopeEncryption("arn://aws:kms/key"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KeyReference.envelopeEncryption("../physical-key"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
