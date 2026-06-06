package com.richie.component.microservice.config.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeignClientOkhttpPropertiesTest {

    @Test
    void defaults_includeOkHttpExtension() {
        FeignClientOkhttpProperties properties = new FeignClientOkhttpProperties();

        assertThat(properties.getOkHttp()).isNotNull();
    }
}
