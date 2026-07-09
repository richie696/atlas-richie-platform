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
package com.richie.component.mqtt.canary;

import com.richie.context.common.api.SpringContextHolder;
import com.richie.contract.gateway.config.DeployConfig;
import com.richie.contract.constant.GlobalConstants;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * MQTT 灰度过滤器（静态服务类）
 * <p>
 * 用于在 MQTT 消息消费端判断是否应该处理消息，实现灰度发布的消息路由。
 * <p>
 * 工作原理：
 * 1. 从 MQTT 5.0 User Properties 获取 X-Canary-Id（门店ID）
 * 2. 判断当前服务实例是否为灰度实例（通过 Nacos 元数据）
 * 3. 判断消息是否为灰度消息（X-Canary-Id 是否在灰度列表中）
 * 4. 灰度实例只处理灰度消息，正常实例只处理正常消息
 * <p>
 * 注意：MQTT 设备是按门店->设备维度，但灰度是按门店维度。
 * 如果某个门店在灰度列表中，该门店下的所有设备消息都应该由灰度实例处理。
 * <p>
 * 使用方式：
 * <pre>
 * if (MqttCanaryFilter.shouldProcess(publish)) {
 *     // 处理消息
 * }
 * </pre>
 * <p>
 * 注意：该类持有静态状态（缓存），用于优化性能，避免重复查询。
 *
 * @author richie696
 * @version 2.0
 * @since 2025-12-09
 */
@Slf4j
public final class MqttCanaryFilter {

    /**
     * 禁止实例化
     */
    private MqttCanaryFilter() {
        throw new UnsupportedOperationException("This is a static service class and cannot be instantiated");
    }

    /**
     * 当前实例是否为灰度实例（缓存，按应用名称缓存）
     * Key: applicationName, Value: isCanaryInstance
     */
    private static final ConcurrentMap<String, Boolean> canaryInstanceCache = new ConcurrentHashMap<>();

    /**
     * 应用名称缓存（避免重复从环境变量获取）
     */
    private static volatile String cachedApplicationName = null;

    /**
     * 判断是否应该处理该 MQTT 消息（静态方法）
     * <p>
     * 规则：
     * - 如果未启用灰度发布，所有消息都处理
     * - 如果消息是灰度消息（X-Canary-Id 在灰度列表中），只有灰度实例处理
     * - 如果消息是正常消息（X-Canary-Id 不在灰度列表中），只有正常实例处理
     * <p>
     * 该方法会自动从 Spring 容器中获取必要的依赖（DeployConfig、DiscoveryClient、CanaryInstanceManager）
     *
     * @param publish MQTT 5.0 发布消息对象
     * @return true 应该处理，false 不应该处理（跳过）
     */
    public static boolean shouldProcess(Mqtt5Publish publish) {
        // 从 Spring 容器获取必要的依赖
        DeployConfig deployConfig = getDeployConfig();
        if (deployConfig == null || !deployConfig.isEnable()) {
            // 如果未启用灰度发布，所有消息都处理
            return true;
        }

        String applicationName = getApplicationName();
        if (applicationName == null || applicationName.trim().isEmpty()) {
            // 如果无法获取应用名称，默认处理所有消息
            return true;
        }

        // 检查当前服务是否参与灰度
        if (!deployConfig.isServiceInCanary(applicationName)) {
            // 如果服务不在灰度列表中，所有消息都处理（不进行灰度过滤）
            if (log.isTraceEnabled()) {
                log.trace("Service {} not in canary list, processing all messages", applicationName);
            }
            return true;
        }

        // 从 MQTT 5.0 User Properties 获取灰度标识（统一使用 ID 模式）
        String canaryId = getUserPropertyValue(publish, GlobalConstants.X_CANARY_ID);

        // 如果没有灰度标识，视为正常消息
        if (StringUtils.isBlank(canaryId)) {
            // 正常消息：只有正常实例处理
            return !isCanaryInstance(applicationName, deployConfig);
        }

        // 判断是否为灰度消息（X-Canary-Id 是否在灰度列表中）
        boolean isCanaryMessage = isCanaryMessage(canaryId, deployConfig);

        // 判断当前实例是否为灰度实例
        boolean isCanary = isCanaryInstance(applicationName, deployConfig);

        // 灰度消息：只有灰度实例处理
        // 正常消息：只有正常实例处理
        boolean shouldProcess = isCanaryMessage == isCanary;

        if (log.isTraceEnabled()) {
            log.trace("MQTT message canary filter: canaryId={}, isCanaryMessage={}, isCanaryInstance={}, shouldProcess={}",
                    canaryId, isCanaryMessage, isCanary, shouldProcess);
        }

        if (!shouldProcess && log.isDebugEnabled()) {
            log.debug("MQTT message skipped by canary filter: canaryId={}, isCanaryMessage={}, isCanaryInstance={}",
                    canaryId, isCanaryMessage, isCanary);
        }

        return shouldProcess;
    }

    /**
     * 判断消息是否为灰度消息
     * <p>
     * 灰度发布统一使用 ID 模式，直接判断 canaryId 是否在灰度列表中
     *
     * @param canaryId    灰度ID（门店ID、用户ID等）
     * @param deployConfig 灰度配置
     * @return true 如果是灰度消息
     */
    private static boolean isCanaryMessage(String canaryId, DeployConfig deployConfig) {
        if (StringUtils.isBlank(canaryId) || deployConfig == null) {
            return false;
        }

        // 判断 X-Canary-Id 是否在灰度列表中
        if (deployConfig.getIdList() == null || deployConfig.getIdList().isEmpty()) {
            return false;
        }

        return deployConfig.getIdList().contains(canaryId);
    }

    /**
     * 判断当前服务实例是否为灰度实例
     * <p>
     * 优先级：
     * 1. 如果存在 CanaryInstanceManager，优先使用（支持配置中心动态更新）
     * 2. 否则从 Nacos 元数据读取（启动时固定）
     * <p>
     * 通过 Nacos 服务注册的元数据判断：
     * - canary = "true" 且 canary-category = "ID" 表示灰度实例
     *
     * @param applicationName 应用名称
     * @param deployConfig    灰度配置
     * @return true 如果是灰度实例
     */
    private static boolean isCanaryInstance(String applicationName, DeployConfig deployConfig) {
        // 如果存在 CanaryInstanceManager，优先使用（支持动态更新）
        CanaryInstanceManager canaryInstanceManager = getCanaryInstanceManager();
        if (canaryInstanceManager != null) {
            // 不使用缓存，每次都查询最新状态（支持动态切换）
            return canaryInstanceManager.isCanaryInstance();
        }

        // 使用缓存，避免重复查询（从 Nacos 元数据读取）
        Boolean cached = canaryInstanceCache.get(applicationName);
        if (cached != null) {
            return cached;
        }

        try {
            DiscoveryClient discoveryClient = getDiscoveryClient();
            if (discoveryClient == null) {
                canaryInstanceCache.put(applicationName, false);
                return false;
            }

            // 获取当前服务实例列表
            List<ServiceInstance> instances = discoveryClient.getInstances(applicationName);
            if (instances == null || instances.isEmpty()) {
                // 如果无法获取实例信息，默认不是灰度实例
                canaryInstanceCache.put(applicationName, false);
                return false;
            }

            // 尝试找到当前实例
            // 策略1：通过 host/port 匹配（如果可能）
            // 策略2：使用第一个实例（在单实例或主实例场景下通常是当前实例）
            ServiceInstance currentInstance = instances.stream()
                    .findFirst()
                    .orElse(null);

            if (currentInstance == null) {
                canaryInstanceCache.put(applicationName, false);
                return false;
            }

            // 从元数据获取灰度标识
            String canaryFlag = currentInstance.getMetadata().get(GlobalConstants.SERVER_CANARY_ENV);
            String canaryCategory = currentInstance.getMetadata().get(GlobalConstants.SERVER_CANARY_CATEGORY);

            // 判断是否为灰度实例
            // canary = "true" 且 canary-category = "ID" 表示灰度实例
            boolean isCanary = Boolean.parseBoolean(canaryFlag)
                    && "ID".equalsIgnoreCase(canaryCategory);

            // 缓存结果
            canaryInstanceCache.put(applicationName, isCanary);

            if (log.isDebugEnabled()) {
                log.debug("Current instance canary status: isCanary={}, canaryFlag={}, canaryCategory={}, instanceId={}",
                        isCanary, canaryFlag, canaryCategory, currentInstance.getInstanceId());
            }

            return isCanary;
        } catch (Exception e) {
            log.warn("Failed to determine canary instance status, defaulting to false: {}", e.getMessage());
            // 发生异常时，默认不是灰度实例
            canaryInstanceCache.put(applicationName, false);
            return false;
        }
    }

    /**
     * 从 MQTT 5.0 User Properties 获取值
     *
     * @param publish     MQTT 5.0 发布消息对象
     * @param propertyKey 用户属性键
     * @return 用户属性值
     */
    private static String getUserPropertyValue(Mqtt5Publish publish, String propertyKey) {
        if (publish == null) {
            return "";
        }

        try {
            return publish.getUserProperties().asList().stream()
                    .filter(prop -> propertyKey.equals(prop.getName().toString()))
                    .map(prop -> prop.getValue().toString())
                    .findFirst()
                    .orElse("");
        } catch (Exception e) {
            log.warn("Failed to get user property value for key: {}, error: {}", propertyKey, e.getMessage());
            return "";
        }
    }

    /**
     * 从 Spring 容器获取 DeployConfig
     *
     * @return DeployConfig 实例，如果不存在则返回 null
     */
    private static DeployConfig getDeployConfig() {
        try {
            return SpringContextHolder.getBean(DeployConfig.class);
        } catch (Exception e) {
            if (log.isTraceEnabled()) {
                log.trace("DeployConfig not found in Spring container: {}", e.getMessage());
            }
            return null;
        }
    }

    /**
     * 从 Spring 容器获取 DiscoveryClient
     *
     * @return DiscoveryClient 实例，如果不存在则返回 null
     */
    private static DiscoveryClient getDiscoveryClient() {
        try {
            return SpringContextHolder.getBean(DiscoveryClient.class);
        } catch (Exception e) {
            if (log.isTraceEnabled()) {
                log.trace("DiscoveryClient not found in Spring container: {}", e.getMessage());
            }
            return null;
        }
    }

    /**
     * 从 Spring 容器获取 CanaryInstanceManager
     *
     * @return CanaryInstanceManager 实例，如果不存在则返回 null
     */
    private static CanaryInstanceManager getCanaryInstanceManager() {
        try {
            return SpringContextHolder.getBean(CanaryInstanceManager.class);
        } catch (Exception e) {
            if (log.isTraceEnabled()) {
                log.trace("CanaryInstanceManager not found in Spring container: {}", e.getMessage());
            }
            return null;
        }
    }

    /**
     * 获取应用名称
     * <p>
     * 优先从缓存获取，如果不存在则从 Spring 环境变量获取
     *
     * @return 应用名称，如果无法获取则返回 null
     */
    private static String getApplicationName() {
        if (cachedApplicationName != null) {
            return cachedApplicationName;
        }

        try {
            Environment environment = SpringContextHolder.getBean(Environment.class);
            String appName = environment.getProperty("spring.application.name", "unknown");
            if (!"unknown".equals(appName)) {
                cachedApplicationName = appName;
                return appName;
            }
        } catch (Exception e) {
            if (log.isTraceEnabled()) {
                log.trace("Failed to get application name from Spring environment: {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * 清除缓存（用于动态更新灰度状态）
     * <p>
     * 当灰度配置更新时，调用此方法清除缓存，触发重新判断灰度状态
     *
     * @param applicationName 应用名称，如果为 null 则清除所有应用的缓存
     */
    public static void clearCache(String applicationName) {
        if (applicationName != null) {
            canaryInstanceCache.remove(applicationName);
            if (log.isDebugEnabled()) {
                log.debug("Cleared canary instance cache for application: {}", applicationName);
            }
        } else {
            canaryInstanceCache.clear();
            cachedApplicationName = null;
            if (log.isDebugEnabled()) {
                log.debug("Cleared all canary instance cache");
            }
        }
    }

    /**
     * 清除所有缓存（用于动态更新灰度状态）
     */
    public static void clearAllCache() {
        clearCache(null);
    }
}
