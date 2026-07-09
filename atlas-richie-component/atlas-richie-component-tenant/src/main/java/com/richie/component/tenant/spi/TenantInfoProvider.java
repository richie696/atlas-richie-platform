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
package com.richie.component.tenant.spi;

import com.richie.component.tenant.model.TenantInfo;

/**
 * 租户信息提供者 SPI。
 *
 * <p>由各接入方实现（通常从 {@code sys_tenant} 表或缓存中读取），
 * 为策略层、拦截器层提供租户运行时信息。</p>
 *
 * <p>默认提供 {@code @ConditionalOnMissingBean} 的 NoOp 实现，
 * 接入方可通过 Spring Bean 覆盖。</p>
 *
 * @author richie696
 * @since 2.0
 */
public interface TenantInfoProvider {

    /**
     * 获取指定租户的运行时信息。
     *
     * @param tenantId 租户 ID（Long 类型）
     * @return 租户信息；如果租户不存在则返回 {@code null}
     */
    TenantInfo getTenantInfo(Long tenantId);

    /**
     * 判断指定租户是否存在。
     *
     * @param tenantId 租户 ID（Long 类型）
     * @return 存在返回 {@code true}
     */
    boolean exists(Long tenantId);
}
