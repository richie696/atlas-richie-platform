/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignatureValueTest {

    @Test
    void protectsStringRepresentationAndRejectsUnboundedInput() {
        SignatureValue value = new SignatureValue("sensitive-signature");

        assertThat(value.toString()).doesNotContain("sensitive-signature");
        assertThatThrownBy(() -> new SignatureValue("a".repeat(64 * 1024 + 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum length");
    }
}
