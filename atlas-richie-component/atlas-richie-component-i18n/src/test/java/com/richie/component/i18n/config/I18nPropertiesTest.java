/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
