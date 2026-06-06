package com.richie.component.microservice.config.properties;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OkhttpExtensionTest {

    @Test
    void defaults_matchExpectedTimeouts() {
        OkhttpExtension extension = new OkhttpExtension();

        assertThat(extension.getReadTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(extension.getWriteTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(extension.getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(extension.getCallTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(extension.getEnableCache()).isFalse();
        assertThat(extension.getHostnameVerification()).isTrue();
    }
}
