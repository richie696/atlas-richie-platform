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
package com.richie.component.mfa.validation.config;

import com.richie.component.mfa.core.config.MfaProperties;
import com.richie.component.mfa.core.support.MfaTenantSupport;
import com.richie.component.mfa.validation.engine.TotpValidationEngine;
import com.richie.component.mfa.validation.replay.ReplayAttackPreventionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * MFA验证模块自动配置类
 * <p>
 * 部署位置：richie-gateway-service
 * 职责：自动配置验证相关的Bean
 *
 * <p><b>租户配置说明</b>：
 * <ul>
 *   <li>租户功能配置统一使用网关的 {@code platform.gateway.tenant.enable} 配置</li>
 *   <li>通过 {@link MfaTenantSupport} 获取租户启用状态</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "platform.component.mfa",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
@RequiredArgsConstructor
public class MfaValidationAutoConfiguration {

    /**
     * TOTP验证引擎 Bean
     * <p>
     * 用于验证 TOTP 验证码，支持时间窗口容错
     *
     * @param properties MFA验证配置属性
     * @return TOTP验证引擎实例
     */
    @Bean
    @ConditionalOnMissingBean
    public TotpValidationEngine totpValidationEngine(MfaProperties properties) {
        return new TotpValidationEngine(properties);
    }

    /**
     * 防重放攻击服务 Bean
     * <p>
     * 用于防止验证码重复使用，标记已使用的验证码
     *
     * @param properties    MFA验证配置属性
     * @param tenantSupport 租户支持类（用于判断是否启用租户功能）
     * @return 防重放攻击服务实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ReplayAttackPreventionService replayAttackPreventionService(
            MfaProperties properties,
            MfaTenantSupport tenantSupport) {
        return new ReplayAttackPreventionService(properties, tenantSupport);
    }

}
