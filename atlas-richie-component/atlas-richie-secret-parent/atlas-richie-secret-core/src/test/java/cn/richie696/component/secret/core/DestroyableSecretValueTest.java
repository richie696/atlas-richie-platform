/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DestroyableSecretValueTest {

    @Test
    void closesAndRejectsFurtherReads() {
        DestroyableSecretValue value = DestroyableSecretValue.ofChars("s3cret".toCharArray());

        assertThat(value.copyChars()).containsExactly('s', '3', 'c', 'r', 'e', 't');
        assertThat(value.toString()).doesNotContain("s3cret");

        value.close();

        assertThat(value.destroyed()).isTrue();
        assertThat(value.size()).isZero();
        assertThatThrownBy(value::copyBytes).isInstanceOf(IllegalStateException.class);
    }
}
