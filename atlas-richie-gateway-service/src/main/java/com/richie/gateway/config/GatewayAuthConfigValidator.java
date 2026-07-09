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
package com.richie.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * 网关认证配置校验器
 * <p>
 * 功能：在应用启动时校验内部系统认证和外部 OAuth2.0 认证不能同时启用
 * <p>
 * 设计原因：
 * - 内部系统认证（AuthenticationFilter）和外部 OAuth2.0 认证（InterfaceAuthFilter）是互斥的
 * - 两者同时启用会导致认证逻辑冲突，影响系统安全性和稳定性
 * - 通过启动时校验，可以提前发现问题，避免部署到生产环境后才发现配置错误
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayAuthConfigValidator implements SmartInitializingSingleton {

    private final GatewayConfig gatewayConfig;

    @Override
    public void afterSingletonsInstantiated() {
        boolean internalAuthEnabled = gatewayConfig.getToken().isEnable();
        boolean interfaceAuthEnabled = gatewayConfig.getOauth2().isEnabled();

        if (internalAuthEnabled && interfaceAuthEnabled) {
            String errorMessage = String.format(
                    "网关认证配置冲突：内部系统认证（platform.gateway.token.enable=true）和外部 OAuth2.0 认证（platform.gateway.oauth2.enabled=true）不能同时启用。\n" +
                            "请根据部署场景选择其中一种认证方式：\n" +
                            "- 内部系统部署：仅启用 platform.gateway.token.enable=true\n" +
                            "- 外部第三方系统部署：仅启用 platform.gateway.oauth2.enabled=true\n" +
                            "当前配置：token.enable=%s, oauth2.enabled=%s",
                    true, true
            );

            log.error("==========================================");
            log.error("网关认证配置校验失败！");
            log.error("==========================================");
            log.error(errorMessage);
            log.error("==========================================");

            throw new IllegalStateException(errorMessage);
        }

        log.info("网关认证配置校验通过：token.enable={}, oauth2.enabled={}",
                internalAuthEnabled, interfaceAuthEnabled);
    }
}
