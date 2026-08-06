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
package cn.richie696.component.tenant.support;

import cn.richie696.component.tenant.config.TenantAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * IT 测试基础设施 — 显式开启租户组件，使 {@link TenantAutoConfiguration}
 * 通过 {@code @ConditionalOnProperty(platform.tenant.enable=true)} 校验并加载,
 * 注册 {@code MultiTenancyProperties} 等所有租户相关 Bean。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(TenantAutoConfiguration.class)
@TestPropertySource(properties = "platform.tenant.enable=true")
public class TenantIntegrationTestConfiguration {
}
