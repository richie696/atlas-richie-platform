/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap.spi;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecretBootstrapResultTest {

    @Test
    void protectsStringRepresentationAndMutableScalarArrays() {
        byte[] source = {1, 2, 3};
        SecretBootstrapResult result = new SecretBootstrapResult(
                "test", "v1", "path", Instant.EPOCH,
                Map.of("password", "sentinel-secret", "binary", source), "request");

        source[0] = 9;
        byte[] firstRead = (byte[]) result.values().get("binary");
        firstRead[1] = 9;

        assertThat((byte[]) result.values().get("binary")).containsExactly(1, 2, 3);
        assertThat(result.toString()).doesNotContain("sentinel-secret");
    }
}
