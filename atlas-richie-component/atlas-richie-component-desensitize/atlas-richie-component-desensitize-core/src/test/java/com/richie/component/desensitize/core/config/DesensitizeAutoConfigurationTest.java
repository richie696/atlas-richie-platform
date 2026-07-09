/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.desensitize.core.config;

import com.richie.component.desensitize.core.service.MaskingService;
import com.richie.component.desensitize.core.util.DesensitizeUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
/**
 * DesensitizeAutoConfigurationTest 测试类。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
class DesensitizeAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DesensitizeAutoConfiguration.class));

    @Test
    void shouldRegisterMaskingServiceAndUtils() {
        contextRunner
                .withPropertyValues(
                        "platform.component.desensitize.sensitive-keys.phone=PHONE"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(MaskingService.class);
                    assertThat(DesensitizeUtils.mask("13812348000",
                            com.richie.component.desensitize.core.model.MaskType.PHONE))
                            .isEqualTo("138****8000");
                });
    }

    @Test
    void disabledSkipsMasking() {
        contextRunner
                .withPropertyValues("platform.component.desensitize.enabled=false")
                .run(context -> {
                    MaskingService service = context.getBean(MaskingService.class);
                    assertThat(service.mask("13812348000",
                            com.richie.component.desensitize.core.model.MaskType.PHONE))
                            .isEqualTo("13812348000");
                });
    }
}
