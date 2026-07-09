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
package com.richie.component.mfa.core.support;

import com.richie.contract.gateway.config.GatewayContract;
import com.richie.contract.gateway.config.TenantFilterConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * MFA 模块租户支持类
 * <p>
 * 统一从跨服务共享契约获取租户启用状态，避免在 MFA 各模块中重复配置。
 * <p>
 * 配置优先级：
 * <ol>
 *   <li>如果存在 {@link GatewayContract} Bean，优先使用 {@code GatewayContract.getTenant().isEnable()}</li>
 *   <li>如果存在 {@link TenantFilterConfig} Bean，使用 {@code TenantFilterConfig.isEnable()}</li>
 *   <li>如果都不存在，返回 {@code false}（默认不启用租户）</li>
 * </ol>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MfaTenantSupport {

    @Autowired(required = false)
    private GatewayContract gatewayContract;

    @Autowired(required = false)
    private TenantFilterConfig tenantFilterConfig;

    /**
     * 判断是否启用租户功能
     * <p>
     * 从跨服务共享契约读取租户启用状态。
     * 如果契约不存在，则返回 {@code false}（默认不启用租户）。
     *
     * @return 是否启用租户功能
     */
    public boolean isTenantEnabled() {
        // 优先级1：使用 GatewayContract（management 模块通常使用此配置）
        if (gatewayContract != null && gatewayContract.getTenant() != null) {
            return gatewayContract.getTenant().isEnable();
        }

        // 优先级2：使用 TenantFilterConfig（validation 模块通常使用此配置）
        if (tenantFilterConfig != null) {
            return tenantFilterConfig.isEnable();
        }

        // 默认值：不启用租户
        log.debug("GatewayContract 和 TenantFilterConfig 均未注入，默认返回 false（不启用租户）");
        return false;
    }
}
