package com.richie.component.i18n.config;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class I18nPropertiesTest {

    @Test
    void defaults_matchExpectedValues() {
        I18nProperties properties = new I18nProperties();

        assertThat(properties.getPath()).isEqualTo("i18n/messages");
        assertThat(properties.getEncoding()).isEqualTo("UTF-8");
        assertThat(properties.getDefaultLocale()).isEqualTo(Locale.CHINA);
        assertThat(properties.getEnableI18nControl()).isFalse();
    }
}
