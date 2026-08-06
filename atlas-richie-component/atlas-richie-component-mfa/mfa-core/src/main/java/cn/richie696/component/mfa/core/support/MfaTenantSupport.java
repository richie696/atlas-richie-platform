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
package cn.richie696.component.mfa.core.support;

import cn.richie696.component.tenant.config.MultiTenancyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * MFA 模块租户支持类
 * <p>
 * 统一从租户组件配置获取租户启用状态，避免在 MFA 各模块中重复配置。
 * <p>
 * 配置优先级：
 * <ol>
 *   <li>如果存在 {@link MultiTenancyProperties} Bean，使用 {@code isEnabled()}</li>
 *   <li>如果不存在，返回 {@code false}（默认不启用租户）</li>
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
    private MultiTenancyProperties properties;

    /**
     * 判断是否启用租户功能
     * <p>
     * 从租户统一配置读取启用状态。如果租户组件未启用或配置 Bean 不存在，
     * 则返回 {@code false}。
     *
     * @return 是否启用租户功能
     */
    public boolean isTenantEnabled() {
        if (properties != null) {
            return properties.isEnable();
        }

        // 默认值：不启用租户
        log.debug("MultiTenancyProperties 未注入，默认返回 false（不启用租户）");
        return false;
    }
}
