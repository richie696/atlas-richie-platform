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
package cn.richie696.component.tenant.config;

import cn.richie696.contract.model.TenantFeature;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 租户功能标志自动配置
 *
 * <p>仅在 {@code platform.tenant.enable=true} 时，将
 * {@link TenantFeature} 的全局标志置为 {@code true}。仅引入依赖不会改变业务系统的 JWT 行为。</p>
 *
 * <p>该配置不区分 Servlet / WebFlux 环境，确保启用租户功能的网关等 Reactive 应用也能注册标志位。</p>
 *
 * @author richie696
 * @since 1.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = MultiTenancyProperties.PREFIX, name = "enable", havingValue = "true")
public class TenantFeatureConfiguration {

    @PostConstruct
    public void init() {
        TenantFeature.setEnabled(true);
    }
}
