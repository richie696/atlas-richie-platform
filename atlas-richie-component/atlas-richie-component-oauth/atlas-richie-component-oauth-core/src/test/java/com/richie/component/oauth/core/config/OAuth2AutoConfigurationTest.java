package com.richie.component.oauth.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2AutoConfigurationTest {

    @Test
    void hasAutoConfigurationAnnotation() {
        OAuth2AutoConfiguration.class.isAnnotationPresent(AutoConfiguration.class);
    }

    @Test
    void hasComponentScanAnnotation() {
        var componentScan = OAuth2AutoConfiguration.class.getAnnotation(ComponentScan.class);
        assertThat(componentScan).isNotNull();
        assertThat(componentScan.value()).contains("com.richie.component.oauth.core");
    }

    @Test
    void hasEnableConfigurationPropertiesAnnotation() {
        var enableConfigProps = OAuth2AutoConfiguration.class.getAnnotation(EnableConfigurationProperties.class);
        assertThat(enableConfigProps).isNotNull();
        assertThat(enableConfigProps.value()).contains(OAuth2Properties.class);
    }
}
