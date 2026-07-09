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
package com.richie.component.messaging.config;

import com.richie.contract.gateway.config.DeployConfig;
import com.richie.component.messaging.filter.CanaryMessageFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 消息组件统一配置类。
 * <p>
 * 扫描消息组件包并注册灰度实例管理器、灰度消息过滤器等 Bean（需 Gateway 与 DiscoveryClient 可用）。
 *
 * @author richie696
 * @version 1.1
 * @since 2020/07/02
 */
@Slf4j
@Configuration
@ComponentScan("com.richie.component.messaging")
@EnableConfigurationProperties({MessagingProperties.class})
public class MessagingConfiguration {

    /**
     * 默认构造函数（供 Spring 使用）。
     */
    public MessagingConfiguration() {
    }

    /**
     * 创建灰度实例管理器 Bean
     * <p>
     * 统一使用 Gateway 的 DeployConfig 作为全局灰度总控开关
     * <p>
     * 条件：
     * 1. DiscoveryClient 存在（服务发现可用）
     * 2. DeployConfig 存在（Gateway 灰度配置可用）
     *
     * @param discoveryClient 服务发现客户端
     * @param applicationName 当前应用名
     * @param deployConfig     Gateway 灰度配置
     * @return 灰度实例管理器
     */
    @Bean
    @ConditionalOnBean({DiscoveryClient.class, DeployConfig.class})
    @ConditionalOnClass({DiscoveryClient.class, DeployConfig.class})
    public CanaryInstanceManager canaryInstanceManager(
            DiscoveryClient discoveryClient,
            @Value("${spring.application.name:unknown}") String applicationName,
            DeployConfig deployConfig) {
        log.info("Canary instance manager enabled for application: {}, using Gateway DeployConfig as global control", applicationName);
        return new CanaryInstanceManager(discoveryClient, applicationName, deployConfig);
    }

    /**
     * 创建灰度消息过滤器 Bean
     * <p>
     * 条件：
     * 1. DeployConfig 存在（灰度配置可用）
     * 2. DiscoveryClient 存在（服务发现可用，用于判断当前实例是否为灰度实例）
     *
     * @param deployConfig          Gateway 灰度配置
     * @param discoveryClient       服务发现客户端
     * @param applicationName       当前应用名
     * @param canaryInstanceManager 灰度实例管理器
     * @return 灰度消息过滤器
     */
    @Bean
    @ConditionalOnBean({DeployConfig.class, DiscoveryClient.class})
    @ConditionalOnClass({DeployConfig.class, DiscoveryClient.class})
    public CanaryMessageFilter canaryMessageFilter(
            DeployConfig deployConfig,
            DiscoveryClient discoveryClient,
            @Value("${spring.application.name:unknown}") String applicationName,
            CanaryInstanceManager canaryInstanceManager) {
        log.info("Canary message filter enabled for application: {}", applicationName);
        return new CanaryMessageFilter(deployConfig, discoveryClient, applicationName, canaryInstanceManager);
    }
}
