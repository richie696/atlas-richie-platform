package com.richie.component.messaging.filter;

import com.richie.contract.gateway.config.DeployConfig;
import com.richie.contract.constant.GlobalConstants;
import com.richie.component.messaging.config.CanaryInstanceManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.messaging.Message;

import java.util.List;
import java.util.Objects;

/**
 * 消息队列灰度过滤器
 * <p>
 * 用于在消息消费端判断是否应该处理消息，实现灰度发布的消息路由。
 * <p>
 * 工作原理：
 * 1. 从消息头获取 X-Canary-Id（门店ID）
 * 2. 判断当前服务实例是否为灰度实例（通过 Nacos 元数据）
 * 3. 判断消息是否为灰度消息（X-Canary-Id 是否在灰度列表中）
 * 4. 灰度实例只处理灰度消息，正常实例只处理正常消息
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-09
 */
@Slf4j
public class CanaryMessageFilter {

    private final DeployConfig deployConfig;
    private final DiscoveryClient discoveryClient;
    private final String applicationName;

    /**
     * 灰度实例管理器（可选，如果存在则优先使用）
     */
    private final CanaryInstanceManager canaryInstanceManager;

    /**
     * 当前实例是否为灰度实例（缓存，仅在未使用 CanaryInstanceManager 时使用）
     */
    private volatile Boolean isCanaryInstance = null;

    /**
     * 构造灰度消息过滤器。
     *
     * @param deployConfig          Gateway 灰度配置
     * @param discoveryClient       服务发现客户端
     * @param applicationName       当前应用名
     * @param canaryInstanceManager 灰度实例管理器（可为 null，为 null 时从 Nacos 元数据判断并缓存）
     */
    public CanaryMessageFilter(DeployConfig deployConfig, DiscoveryClient discoveryClient, String applicationName, CanaryInstanceManager canaryInstanceManager) {
        this.deployConfig = deployConfig;
        this.discoveryClient = discoveryClient;
        this.applicationName = applicationName;
        this.canaryInstanceManager = canaryInstanceManager;
    }

    /**
     * 判断是否应该处理该消息
     * <p>
     * 规则：
     * - 如果未启用灰度发布，所有消息都处理
     * - 如果消息是灰度消息（X-Canary-Id 在灰度列表中），只有灰度实例处理
     * - 如果消息是正常消息（X-Canary-Id 不在灰度列表中），只有正常实例处理
     *
     * @param message 消息对象
     * @return true 应该处理，false 不应该处理（跳过）
     */
    public boolean shouldProcess(Message<?> message) {
        // 如果未启用灰度发布，所有消息都处理
        if (deployConfig == null || !deployConfig.isEnable()) {
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

        // 灰度发布统一使用 ID 模式，无需检查类型

        // 从消息头获取灰度标识（统一使用 ID 模式）
        String canaryId = getHeaderValue(message, GlobalConstants.X_CANARY_ID);

        // 如果没有灰度标识，视为正常消息
        if (StringUtils.isBlank(canaryId)) {
            // 正常消息：只有正常实例处理
            return !isCanaryInstance();
        }

        // 判断是否为灰度消息（X-Canary-Id 是否在灰度列表中）
        boolean isCanaryMessage = isCanaryMessage(canaryId);

        // 判断当前实例是否为灰度实例
        boolean isCanary = isCanaryInstance();

        // 灰度消息：只有灰度实例处理
        // 正常消息：只有正常实例处理
        boolean shouldProcess = isCanaryMessage == isCanary;

        if (log.isTraceEnabled()) {
            log.trace("Message canary filter: canaryId={}, isCanaryMessage={}, isCanaryInstance={}, shouldProcess={}",
                    canaryId, isCanaryMessage, isCanary, shouldProcess);
        }

        if (!shouldProcess && log.isDebugEnabled()) {
            log.debug("Message skipped by canary filter: canaryId={}, isCanaryMessage={}, isCanaryInstance={}",
                    canaryId, isCanaryMessage, isCanary);
        }

        return shouldProcess;
    }

    /**
     * 判断消息是否为灰度消息
     * <p>
     * 灰度发布统一使用 ID 模式，直接判断 canaryId 是否在灰度列表中
     *
     * @param canaryId 灰度ID（门店ID、用户ID等）
     * @return true 如果是灰度消息
     */
    private boolean isCanaryMessage(String canaryId) {
        if (StringUtils.isBlank(canaryId)) {
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
     * @return true 如果是灰度实例
     */
    private boolean isCanaryInstance() {
        // 如果存在 CanaryInstanceManager，优先使用（支持动态更新）
        if (canaryInstanceManager != null) {
            // 不使用缓存，每次都查询最新状态（支持动态切换）
            return canaryInstanceManager.isCanaryInstance();
        }

        // 使用缓存，避免重复查询（从 Nacos 元数据读取）
        if (isCanaryInstance != null) {
            return isCanaryInstance;
        }

        try {
            // 获取当前服务实例列表
            List<ServiceInstance> instances = discoveryClient.getInstances(applicationName);
            if (instances == null || instances.isEmpty()) {
                // 如果无法获取实例信息，默认不是灰度实例
                isCanaryInstance = false;
                return false;
            }

            // 尝试找到当前实例
            // 策略1：通过 host/port 匹配（如果可能）
            // 策略2：使用第一个实例（在单实例或主实例场景下通常是当前实例）
            ServiceInstance currentInstance = instances.stream()
                    .findFirst()
                    .orElse(null);

            if (currentInstance == null) {
                isCanaryInstance = false;
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
            this.isCanaryInstance = isCanary;

            if (log.isDebugEnabled()) {
                log.debug("Current instance canary status: isCanary={}, canaryFlag={}, canaryCategory={}, instanceId={}",
                        isCanary, canaryFlag, canaryCategory, currentInstance.getInstanceId());
            }

            return isCanary;
        } catch (Exception e) {
            log.warn("Failed to determine canary instance status, defaulting to false: {}", e.getMessage());
            // 发生异常时，默认不是灰度实例
            isCanaryInstance = false;
            return false;
        }
    }

    /**
     * 从消息头获取值
     *
     * @param message  消息对象
     * @param headerKey 请求头键
     * @return 请求头值
     */
    private String getHeaderValue(Message<?> message, String headerKey) {
        Object value = message.getHeaders().get(headerKey);
        return Objects.toString(value, "");
    }

    /**
     * 清除缓存（用于动态更新灰度状态，由 CanaryInstanceManager 在配置变更时调用）。
     */
    public void clearCache() {
        isCanaryInstance = null;
    }
}
