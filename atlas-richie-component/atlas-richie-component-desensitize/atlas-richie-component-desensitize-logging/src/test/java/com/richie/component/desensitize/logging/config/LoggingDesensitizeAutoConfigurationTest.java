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
package com.richie.component.desensitize.logging.config;

import com.richie.component.desensitize.logging.logback.SensitiveLogArgTurboFilter;
import com.richie.component.desensitize.logging.logback.SensitiveMdcTurboFilter;
import com.richie.component.desensitize.logging.service.LoggingMaskingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
/**
 * LoggingDesensitizeAutoConfigurationTest 测试类。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
class LoggingDesensitizeAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    com.richie.component.desensitize.core.config.DesensitizeAutoConfiguration.class,
                    LoggingDesensitizeAutoConfiguration.class))
            .withPropertyValues("platform.component.desensitize.sensitive-keys.phone=PHONE");

    @Test
    void shouldCreateLoggingMaskingServiceBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LoggingMaskingService.class);
            assertThat(context).hasSingleBean(SensitiveLogArgTurboFilter.class);
            assertThat(context).hasSingleBean(SensitiveMdcTurboFilter.class);
        });
    }

    @Test
    void shouldDisableArgTurboFilterWhenConfigured() {
        contextRunner
                .withPropertyValues("platform.component.desensitize.log.features.sensitive-log-arg-turbo-filter-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(LoggingMaskingService.class);
                    assertThat(context).doesNotHaveBean(SensitiveLogArgTurboFilter.class);
                    assertThat(context).hasSingleBean(SensitiveMdcTurboFilter.class);
                });
    }

    @Test
    void shouldDisableMdcTurboFilterWhenConfigured() {
        contextRunner
                .withPropertyValues("platform.component.desensitize.log.features.sensitive-mdc-turbo-filter-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(LoggingMaskingService.class);
                    assertThat(context).hasSingleBean(SensitiveLogArgTurboFilter.class);
                    assertThat(context).doesNotHaveBean(SensitiveMdcTurboFilter.class);
                });
    }

    @Test
    void shouldDisableAutoRegisterTurboFiltersWhenConfigured() {
        contextRunner
                .withPropertyValues("platform.component.desensitize.log.features.auto-register-turbo-filters=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(LoggingMaskingService.class);
                    assertThat(context).hasSingleBean(SensitiveLogArgTurboFilter.class);
                    assertThat(context).hasSingleBean(SensitiveMdcTurboFilter.class);
                    assertThat(context).doesNotHaveBean("loggingTurboFilterRegistrar");
                });
    }
}

