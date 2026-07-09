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
package com.richie.component.messaging.config;

import com.richie.contract.constant.GlobalConstants;
import com.richie.contract.gateway.config.DeployConfig;
import com.richie.component.messaging.filter.CanaryMessageFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.event.EventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 灰度实例管理器
 * <p>
 * 统一使用 Gateway 的 DeployConfig 作为全局灰度总控开关，实现：
 * 1. Gateway 配置统一控制 HTTP 请求和消息队列的灰度
 * 2. 通过配置中心动态更新灰度状态（platform.gateway.deploy）
 * 3. 自动刷新 CanaryMessageFilter 缓存
 * <p>
 * 配置路径：platform.gateway.deploy（与 Gateway 配置统一）
 *
 * @author richie696
 * @version 2.0
 * @since 2025-12-09
 */
@Slf4j
@RefreshScope
public class CanaryInstanceManager {

    /** 服务发现客户端，用于获取当前实例列表与元数据 */
    private final DiscoveryClient discoveryClient;
    /** 当前应用名 */
    private final String applicationName;

    /**
     * Gateway 灰度配置（全局总控开关），统一使用 Gateway 的 DeployConfig，实现全局灰度统一管理
     */
    private final DeployConfig deployConfig;

    /** 灰度消息过滤器（可选），配置刷新时用于清除其缓存 */
    @Autowired(required = false)
    private CanaryMessageFilter canaryMessageFilter;

    /**
     * 构造灰度实例管理器。
     *
     * @param discoveryClient 服务发现客户端
     * @param applicationName 当前应用名
     * @param deployConfig     Gateway 灰度配置
     */
    public CanaryInstanceManager(
            DiscoveryClient discoveryClient,
            @Value("${spring.application.name:unknown}") String applicationName,
            DeployConfig deployConfig) {
        this.discoveryClient = discoveryClient;
        this.applicationName = applicationName;
        this.deployConfig = deployConfig;
    }

    /**
     * 获取当前实例的灰度状态
     * <p>
     * 优先级：
     * 1. Gateway 配置（platform.gateway.deploy）- 全局总控
     * 2. Nacos 元数据（服务注册时的 canary 标记）- 兜底方案
     *
     * @return true 如果是灰度实例
     */
    public boolean isCanaryInstance() {
        // 优先使用 Gateway 配置（全局总控）
        if (deployConfig != null && deployConfig.isEnable()) {
            // 如果 Gateway 启用了灰度发布，从 Nacos 元数据判断当前实例是否为灰度实例
            return isCanaryInstanceFromMetadata();
        }

        // 如果 Gateway 配置未启用灰度，从 Nacos 元数据读取（兜底方案）
        return isCanaryInstanceFromMetadata();
    }

    /**
     * 从 Nacos 元数据判断当前实例是否为灰度实例
     *
     * @return true 如果是灰度实例
     */
    private boolean isCanaryInstanceFromMetadata() {
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(applicationName);
            if (instances.isEmpty()) {
                return false;
            }

            ServiceInstance currentInstance = instances.getFirst();

            String canaryFlag = currentInstance.getMetadata().get(GlobalConstants.SERVER_CANARY_ENV);

            return Boolean.parseBoolean(canaryFlag);
        } catch (Exception e) {
            log.warn("Failed to get canary status from Nacos metadata: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 清除缓存，触发重新判断灰度状态
     * <p>
     * 当 Gateway 配置（platform.gateway.deploy）更新时，调用此方法清除缓存
     */
    public void clearCache() {
        log.info("Clearing canary filter cache due to configuration change");
        if (canaryMessageFilter != null) {
            canaryMessageFilter.clearCache();
        }
    }

    /**
     * 监听配置刷新事件，自动清除缓存
     * <p>
     * 监听 Gateway 配置（platform.gateway.deploy）的变更
     */
    @EventListener
    public void onEnvironmentChange(EnvironmentChangeEvent event) {
        // 检查是否涉及 Gateway 灰度配置的变更
        if (event.getKeys().stream().anyMatch(key -> key.contains("platform.gateway.deploy"))) {
            log.info("Gateway canary configuration changed, clearing canary filter cache. Changed keys: {}", event.getKeys());
            clearCache();
        }
    }

    /**
     * 获取当前灰度配置与实例状态信息（用于 actuator 等查询）。
     *
     * @return 包含 gatewayCanaryEnabled、isCanaryInstance、applicationName 等键的 Map，不会为 null
     */
    public Map<String, Object> getCanaryInfo() {
        Map<String, Object> info = new HashMap<>();
        if (deployConfig != null) {
            info.put("gatewayCanaryEnabled", deployConfig.isEnable());
            info.put("gatewayCanaryIdList", deployConfig.getIdList());
            info.put("gatewayCanaryServiceList", deployConfig.getServiceList());
            info.put("isServiceInCanary", deployConfig.isServiceInCanary(applicationName));
        } else {
            info.put("gatewayCanaryEnabled", false);
            info.put("gatewayCanaryIdList", null);
            info.put("gatewayCanaryServiceList", null);
            info.put("isServiceInCanary", false);
        }
        info.put("isCanaryInstance", isCanaryInstance());
        info.put("applicationName", applicationName);
        info.put("configSource", "platform.gateway.deploy (Global Control)");
        return info;
    }
}
