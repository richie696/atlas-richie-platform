package com.richie.component.storage.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AzureBlobAutoConfigurationSmokeTest {

    @Test
    void configurationClass_isPresent() {
        assertThat(AzureBlobAutoConfiguration.class).isNotNull();
    }
}
