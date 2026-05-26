package com.richie.component.messaging.pulsar.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 灰度实例管理接口
 * <p>
 * 提供 HTTP 接口用于查询和切换灰度状态
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-09
 */
@Slf4j
@RestController
@RequestMapping("/actuator/canary")
@RequiredArgsConstructor
@ConditionalOnWebApplication
@ConditionalOnBean(CanaryInstanceManager.class)
public class CanaryInstanceController {

    /** 灰度实例管理器 */
    private final CanaryInstanceManager canaryInstanceManager;
    /** 配置刷新器，用于触发 Nacos 配置重新加载 */
    @Qualifier("configDataContextRefresher")
    private final ContextRefresher contextRefresher;

    /**
     * 查询当前灰度状态。
     *
     * @return 灰度状态信息（含 isCanaryInstance、gatewayCanaryEnabled 等），不会为 null
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return canaryInstanceManager.getCanaryInfo();
    }

    /**
     * 刷新灰度配置
     * <p>
     * 注意：此接口用于触发配置刷新，实际配置需要在 Nacos 配置中心修改 Gateway 配置
     * <p>
     * 使用方式：
     * 1. 在 Nacos 配置中心修改 Gateway 配置：
     *    platform.gateway.deploy.enable=true/false
     *    platform.gateway.deploy.id-list=[门店ID列表]
     * 2. 调用此接口触发配置刷新
     *
     * @return 操作结果（含 success、message、currentStatus），不会为 null
     */
    @PostMapping("/refresh")
    public Map<String, Object> refresh() {
        log.info("Refreshing Gateway canary configuration from config center");

        // 触发配置刷新
        contextRefresher.refresh();

        // 清除缓存
        canaryInstanceManager.clearCache();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Gateway canary configuration refreshed. Config path: platform.gateway.deploy");
        result.put("currentStatus", canaryInstanceManager.getCanaryInfo());

        return result;
    }
}
