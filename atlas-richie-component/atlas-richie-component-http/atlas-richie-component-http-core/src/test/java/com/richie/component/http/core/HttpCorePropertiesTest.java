package com.richie.component.http.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpCorePropertiesTest {

    @Test
    void defaultsToStrictSsl() {
        HttpCoreProperties properties = new HttpCoreProperties();

        assertThat(properties.isStrictSsl()).isTrue();
        assertThat(properties.getProvider()).isNull();
    }

    @Test
    void providerCanBeConfigured() {
        HttpCoreProperties properties = new HttpCoreProperties();
        properties.setProvider(HttpProvider.JDK);

        assertThat(properties.getProvider()).isEqualTo(HttpProvider.JDK);
        properties.setStrictSsl(false);
        assertThat(properties.isStrictSsl()).isFalse();
    }
}
