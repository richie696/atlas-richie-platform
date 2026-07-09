/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.i18n.resolver;

import com.richie.component.i18n.config.I18nProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultI18nResolverTest {

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void get_usesLocaleFromContextHolder() {
        MessageSource messageSource = mock(MessageSource.class);
        I18nProperties properties = new I18nProperties();
        DefaultI18nResolver resolver = new DefaultI18nResolver(messageSource, properties);
        LocaleContextHolder.setLocale(Locale.US);
        when(messageSource.getMessage(eq("greeting"), isNull(), eq(Locale.US))).thenReturn("Hello");

        assertThat(resolver.get("greeting")).isEqualTo("Hello");
    }

    @Test
    void get_withNullLocale_usesDefaultFromProperties() {
        MessageSource messageSource = mock(MessageSource.class);
        I18nProperties properties = new I18nProperties();
        properties.setDefaultLocale(Locale.GERMANY);
        DefaultI18nResolver resolver = new DefaultI18nResolver(messageSource, properties);
        when(messageSource.getMessage(eq("title"), isNull(), eq(Locale.GERMANY))).thenReturn("Hallo");

        assertThat(resolver.get((Locale) null, "title")).isEqualTo("Hallo");
    }

    @Test
    void get_appliesMessageFormatterWhenArgsPresent() {
        MessageSource messageSource = mock(MessageSource.class);
        I18nProperties properties = new I18nProperties();
        DefaultI18nResolver resolver = new DefaultI18nResolver(messageSource, properties);
        when(messageSource.getMessage(eq("welcome"), isNull(), eq(Locale.CHINA))).thenReturn("欢迎, {}!");

        assertThat(resolver.get(Locale.CHINA, "welcome", "Richie")).isEqualTo("欢迎, Richie!");
    }
}
