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
package com.richie.component.tenant.spi;

import com.richie.component.tenant.model.TenantInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认 {@link TenantInfoProvider} 占位实现。
 *
 * <p>由 {@code TenantAutoConfiguration} 通过 {@code @ConditionalOnMissingBean} 自动注册,
 * 当接入方未提供自己的 SPI 实现时作为兜底。生产环境接入方<b>必须</b>用自己的实现覆盖此 Bean,
 * 否则策略层 / 拦截器层将无法查询租户信息，所有 SQL 路由会失效。</p>
 *
 * <p>判定方法：通过 {@link com.richie.component.tenant.healthcheck.TenantHealthIndicator}
 * （启用 {@code multi-tenancy.health-check.enabled=true}）启动期检查当前 Bean 仍是本类则拒绝启动。</p>
 *
 * @author richie696
 * @since 2.0
 */
public class NoOpTenantInfoProvider implements TenantInfoProvider {

    private static final Logger log = LoggerFactory.getLogger(NoOpTenantInfoProvider.class);

    @Override
    public TenantInfo getTenantInfo(Long tenantId) {
        log.debug("NoOpTenantInfoProvider.getTenantInfo({}) -> null", tenantId);
        return null;
    }

    @Override
    public boolean exists(Long tenantId) {
        return false;
    }
}
