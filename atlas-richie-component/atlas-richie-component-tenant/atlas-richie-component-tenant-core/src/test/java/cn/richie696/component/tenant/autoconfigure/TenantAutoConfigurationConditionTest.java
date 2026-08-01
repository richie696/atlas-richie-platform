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
package cn.richie696.component.tenant.autoconfigure;

import cn.richie696.component.tenant.config.TenantAutoConfiguration;
import cn.richie696.component.tenant.config.TenantFeatureConfiguration;
import cn.richie696.contract.model.TenantFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class TenantAutoConfigurationConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    TenantAutoConfiguration.class,
                    TenantFeatureConfiguration.class));

    @AfterEach
    void resetTenantFeature() {
        TenantFeature.setEnabled(false);
    }

    @Test
    void shouldNotConfigureTenantInfrastructureWhenPropertyIsMissing() {
        TenantFeature.setEnabled(false);

        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean("tenantInfoProvider");
            assertThat(context).doesNotHaveBean("tenantTaskDecorator");
            assertThat(context).doesNotHaveBean("tenantMetaObjectHandler");
            assertThat(TenantFeature.isEnabled()).isFalse();
        });
    }

    @Test
    void shouldConfigureTenantInfrastructureOnlyWhenExplicitlyEnabled() {
        TenantFeature.setEnabled(false);

        contextRunner.withPropertyValues("platform.tenant.enable=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(cn.richie696.component.tenant.config.MultiTenancyProperties.class);
                    assertThat(context).hasBean("tenantInfoProvider");
                    assertThat(context).hasBean("tenantTaskDecorator");
                    assertThat(TenantFeature.isEnabled()).isTrue();
                });
    }
}
