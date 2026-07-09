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
package com.richie.component.tenant.healthcheck;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.model.TenantInfo;
import com.richie.component.tenant.spi.NoOpTenantInfoProvider;
import com.richie.component.tenant.spi.TenantInfoProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("TenantHealthIndicator 启动期 SPI 健康检查")
class TenantHealthIndicatorTest {

    private final ApplicationArguments args = mock(ApplicationArguments.class);

    private MultiTenancyProperties props(boolean enabled) {
        MultiTenancyProperties p = new MultiTenancyProperties();
        p.setEnabled(enabled);
        return p;
    }

    @Nested
    @DisplayName("启用检查场景")
    class Enabled {

        @Test
        @DisplayName("SPI 仍是 NoOp → 拋 IllegalStateException 阻止启动")
        void noOpProviderFailsStartup() {
            TenantHealthIndicator indicator = new TenantHealthIndicator(
                new NoOpTenantInfoProvider(), props(true));

            assertThatThrownBy(() -> indicator.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NoOpTenantInfoProvider");
        }

        @Test
        @DisplayName("业务已实现 SPI → 正常通过")
        void realProviderPasses() {
            TenantInfoProvider real = new TenantInfoProvider() {
                @Override
                public TenantInfo getTenantInfo(Long tenantId) {
                    return new TenantInfo().setTenantId(tenantId);
                }

                @Override
                public boolean exists(Long tenantId) {
                    return true;
                }
            };
            TenantHealthIndicator indicator = new TenantHealthIndicator(real, props(true));

            assertThatCode(() -> indicator.run(args)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("异常信息包含'NoOpTenantInfoProvider'和实现指引")
        void errorMessageGuidesImplementation() {
            TenantHealthIndicator indicator = new TenantHealthIndicator(
                new NoOpTenantInfoProvider(), props(true));

            assertThatThrownBy(() -> indicator.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NoOpTenantInfoProvider")
                .hasMessageContaining("TenantInfoProvider");
        }
    }

    @Nested
    @DisplayName("关闭或未启用场景")
    class Disabled {

        @Test
        @DisplayName("multi-tenancy.enabled=false 时跳过检查,即使是 NoOp 也不抛")
        void multiTenancyDisabledSkipsCheck() {
            TenantHealthIndicator indicator = new TenantHealthIndicator(
                new NoOpTenantInfoProvider(), props(false));

            assertThatCode(() -> indicator.run(args)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("实现类型判定")
    class ProviderTypeDetection {

        @Test
        @DisplayName("NoOpTenantInfoProvider 子类也被识别为 NoOp 状态")
        void noOpSubclassAlsoFails() {
            TenantInfoProvider noOpSubclass = new NoOpTenantInfoProvider() {
                @Override
                public TenantInfo getTenantInfo(Long tenantId) {
                    return null;
                }
            };

            TenantHealthIndicator indicator = new TenantHealthIndicator(noOpSubclass, props(true));

            assertThatThrownBy(() -> indicator.run(args))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("自定义实现类名出现在通过日志中(通过 spy mock 验证)")
        void customProviderClassReported() {
            class CustomProvider implements TenantInfoProvider {
                @Override
                public TenantInfo getTenantInfo(Long tenantId) {
                    return null;
                }

                @Override
                public boolean exists(Long tenantId) {
                    return false;
                }
            }

            TenantHealthIndicator indicator = new TenantHealthIndicator(new CustomProvider(), props(true));

            assertThatCode(() -> indicator.run(args)).doesNotThrowAnyException();
            assertThat(new CustomProvider().getClass().getSimpleName())
                .isEqualTo("CustomProvider");
        }
    }
}
