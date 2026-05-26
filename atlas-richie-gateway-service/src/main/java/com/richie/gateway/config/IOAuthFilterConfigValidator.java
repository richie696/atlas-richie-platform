package com.richie.gateway.config;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * OAuth2.0 接口授权过滤器配置验证器
 * <p>
 * 在应用启动时验证 OAuth2.0 配置的必填项，如果验证失败则阻止启动
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-XX
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IOAuthFilterConfigValidator implements ApplicationListener<ApplicationReadyEvent> {

    private final GatewayConfig gatewayConfig;

    @Override
    public void onApplicationEvent(@Nonnull ApplicationReadyEvent event) {
        IOAuthFilterConfig config = gatewayConfig.getInterfaceAuth();
        if (config == null) {
            log.warn("OAuth2.0 接口授权过滤器配置未找到，跳过验证");
            return;
        }

        // 如果未启用，则不需要验证
        if (!config.isEnable()) {
            log.debug("OAuth2.0 接口授权过滤器未启用，跳过配置验证");
            return;
        }

        // 验证必填项
        List<String> missingFields = validateRequiredFields(config);

        if (!missingFields.isEmpty()) {
            String errorMessage = buildErrorMessage(missingFields);
            log.error("");
            log.error("==========================================");
            log.error("【OAuth2.0 配置验证失败】");
            log.error("==========================================");
            log.error("错误信息：{}", errorMessage);
            log.error("");
            log.error("请在配置文件中补充以下必填项：");
            for (String field : missingFields) {
                log.error("  ❌ {}", field);
            }
            log.error("");
            log.error("配置示例：");
            log.error("  platform:");
            log.error("    gateway:");
            log.error("      interface-auth:");
            log.error("        enable: true");
            log.error("        token-secret: your-secret-key-here");
            log.error("");
            log.error("注意：IP 白名单按客户端（clientId）管理，存储在 Redis 缓存中，无需在此配置");
            log.error("");
            log.error("==========================================");
            throw new IllegalStateException(errorMessage);
        }

        log.info("OAuth2.0 接口授权过滤器配置验证通过");
    }

    /**
     * 验证必填字段
     *
     * @param config OAuth2.0 配置
     * @return 缺失的必填字段列表
     */
    private List<String> validateRequiredFields(IOAuthFilterConfig config) {
        List<String> missingFields = new ArrayList<>();

        // tokenSecret 是必填项
        if (StringUtils.isBlank(config.getTokenSecret())) {
            missingFields.add("tokenSecret（OAuth2.0 Token 签发密钥）");
        }

        return missingFields;
    }

    /**
     * 构建错误消息
     *
     * @param missingFields 缺失的必填字段列表
     * @return 错误消息
     */
    private String buildErrorMessage(List<String> missingFields) {
        if (missingFields.size() == 1) {
            return String.format("OAuth2.0 接口授权过滤器已启用，但缺少必填配置项：%s", missingFields.getFirst());
        } else {
            return String.format("OAuth2.0 接口授权过滤器已启用，但缺少 %d 个必填配置项：%s",
                    missingFields.size(), String.join("、", missingFields));
        }
    }
}
