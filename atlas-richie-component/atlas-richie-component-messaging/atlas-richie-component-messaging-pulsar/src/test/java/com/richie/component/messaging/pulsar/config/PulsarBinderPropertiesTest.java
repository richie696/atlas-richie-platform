package com.richie.component.messaging.pulsar.config;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("""
        Spring Boot 4.0.6 与 spring-pulsar 2.0.5 兼容性问题：
        PulsarBinderConfigurationProperties 父类构造函数引用
        org.springframework.boot.autoconfigure.pulsar.PulsarProperties$Consumer，
        该内部类在 Spring Boot 4.0.6 已被移除/重构。
        待 spring-pulsar 升级到适配 SB 4.0.6 的版本后恢复。
        """)
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
