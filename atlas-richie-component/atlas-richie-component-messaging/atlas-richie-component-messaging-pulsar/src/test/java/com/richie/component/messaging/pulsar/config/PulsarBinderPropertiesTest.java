package com.richie.component.messaging.pulsar.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PulsarBinderPropertiesTest {

    @Test
    void canInstantiateAndUsesExpectedPrefix() {
        PulsarBinderProperties properties = new PulsarBinderProperties();

        assertThat(properties).isNotNull();
        assertThat(PulsarBinderProperties.class.getAnnotation(
                org.springframework.boot.context.properties.ConfigurationProperties.class).prefix())
                .isEqualTo("spring.cloud.stream.pulsar.binder");
    }
}
