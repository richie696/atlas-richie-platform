package com.richie.component.web.utils;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebUtilsTest {

    @Test
    void refreshHttpMessageConverter_replacesJacksonConverters() {
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        converters.add(new JacksonJsonHttpMessageConverter());

        WebUtils.refreshHttpMessageConverter(converters);

        assertThat(converters).hasSize(1);
        assertThat(converters.getFirst()).isInstanceOf(JacksonJsonHttpMessageConverter.class);
    }

    @Test
    void refreshHttpMessageConverter_leavesNonJacksonConvertersUntouched() {
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        var original = new JacksonJsonHttpMessageConverter();
        converters.add(original);
        converters.add(new StringHttpMessageConverter());

        WebUtils.refreshHttpMessageConverter(converters);

        assertThat(converters).hasSize(2);
        assertThat(converters.get(0)).isInstanceOf(JacksonJsonHttpMessageConverter.class);
        assertThat(converters.get(0)).isNotSameAs(original);
    }
}
