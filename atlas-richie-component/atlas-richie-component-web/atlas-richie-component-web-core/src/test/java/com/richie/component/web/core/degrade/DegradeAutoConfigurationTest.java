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
package com.richie.component.web.core.degrade;

import com.richie.component.web.core.config.degrade.DegradeAutoConfiguration;
import com.richie.component.web.core.config.degrade.DegradeProperties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DegradeAutoConfiguration} Spring 上下文装配集成测试。
 */
class DegradeAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DegradeAutoConfiguration.class));

    @Test
    void defaultLoads_registryAndInterceptor() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(DegradeStrategyRegistry.class);
            assertThat(ctx).hasSingleBean(DefaultDegradeStrategyRegistry.class);
            assertThat(ctx).hasSingleBean(DegradeInterceptor.class);
            assertThat(ctx).hasSingleBean(DegradeProperties.class);
        });
    }

    @Test
    void disabledProperty_skipsInterceptorButKeepsRegistry() {
        runner.withPropertyValues("platform.component.web.degrade.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(DegradeStrategyRegistry.class);
                    assertThat(ctx).doesNotHaveBean(DegradeInterceptor.class);
                });
    }

    @Test
    void customRegistry_beatsDefault() {
        DegradeStrategyRegistry custom = new DefaultDegradeStrategyRegistry();
        runner.withBean("customRegistry", DegradeStrategyRegistry.class, () -> custom)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(DegradeStrategyRegistry.class);
                    assertThat(ctx.getBean(DegradeStrategyRegistry.class)).isSameAs(custom);
                });
    }
}