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
package com.richie.component.tenant.config;

import com.richie.contract.model.TenantFeature;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * 租户功能标志自动配置
 *
 * <p>在任何环境下（Servlet / WebFlux）将 {@link TenantFeature} 的全局标志置为 {@code true}，
 * 标识 {@code atlas-richie-component-tenant} 已加载到 classpath 中。</p>
 *
 * <p>{@link TenantAutoConfiguration} 带有 {@code @ConditionalOnWebApplication(SERVLET)} 限制，
 * 该配置类没有环境限制，确保在 WebFlux（例如网关）中也能注册标志位。</p>
 *
 * @author richie696
 * @since 1.0
 */
@AutoConfiguration
public class TenantFeatureConfiguration {

    @PostConstruct
    public void init() {
        TenantFeature.setEnabled(true);
    }
}
