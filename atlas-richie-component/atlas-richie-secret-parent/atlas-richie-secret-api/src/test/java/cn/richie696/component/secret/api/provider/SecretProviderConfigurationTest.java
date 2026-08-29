/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api.provider;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecretProviderConfigurationTest {

    @Test
    void protectsPropertiesAndMutableSecretArrays() {
        char[] token = "sentinel-token".toCharArray();
        SecretProviderConfiguration configuration = new SecretProviderConfiguration(
                "test", Map.of("token", token));

        token[0] = 'x';
        char[] firstRead = (char[]) configuration.properties().get("token");
        firstRead[1] = 'x';

        assertThat((char[]) configuration.properties().get("token"))
                .containsExactly("sentinel-token".toCharArray());
        assertThat(configuration.toString()).doesNotContain("sentinel-token");
    }
}
