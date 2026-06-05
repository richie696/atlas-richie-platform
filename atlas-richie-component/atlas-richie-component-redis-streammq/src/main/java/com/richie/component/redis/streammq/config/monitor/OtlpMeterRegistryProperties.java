package com.richie.component.redis.streammq.config.monitor;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OTLP MeterRegistry 配置属性
 *
 * <p>用于配置 OTLP 指标导出的相关参数。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-01-29
 */
@Data
@ConfigurationProperties(prefix = "management.otlp.metrics")
public class OtlpMeterRegistryProperties {

    /**
     * 是否启用 OTLP 指标导出
     */
    private boolean enabled = false;

    /**
     * OTLP 服务端点
     */
    private String endpoint;

    /**
     * 服务名称
     */
    private String serviceName = "redis-stream-service";

    /**
     * 超时时间（秒）
     */
    private int timeoutSeconds = 30;
}
