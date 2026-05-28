package com.richie.component.cache.redis.config.monitor;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Redis Stream 监控配置属性
 *
 * <p>该配置类用于统一控制 Redis Stream 相关的健康检查、指标采集、性能统计、错误监控、
 * 以及业务指标采集的开关、粒度与采样强度。所有配置均可通过 Spring Boot 配置中心
 * （application.yml / Nacos / Apollo 等）进行动态调整。
 *
 * <p><strong>整体原则与影响说明：</strong>
 * <ul>
 *   <li><strong>enabled</strong>：总开关，关闭后将跳过所有监控逻辑（包括健康检查与指标采集）。
 *       适用于本地调试或对性能极端敏感的场景。</li>
 *   <li><strong>metrics</strong>：控制指标是否上报、是否开启直方图/分位数（detailed）、采样率等。
 *       指标越详细，内存与 CPU 开销越高；采样率越高，数据越精确，开销越大。</li>
 *   <li><strong>performance</strong>：控制是否记录处理、拉取、发布等关键路径的耗时指标。开启后可用于性能瓶颈定位，
 *       但在高 QPS 下建议结合采样率使用。</li>
 *   <li><strong>errorMonitoring</strong>：控制是否采集错误、是否按类型分类、是否记录堆栈。记录堆栈会显著增加开销，
 *       仅在问题定位时临时开启。</li>
 *   <li><strong>businessMonitoring</strong>：控制业务层面计数（发布/消费/确认/重试等）。通常开销较低，推荐开启。</li>
 * </ul>
 *
 * <p><strong>生产环境推荐配置（示例）：</strong>
 * <pre>
 * platform:
 *   cache:
 *     redis:
 *       stream:
 *         monitoring:
 *           enabled: true                       # 生产建议开启
 *           metrics:
 *             enabled: true                     # 上报指标
 *             detailed: false                   # 默认关闭直方图/分位数，必要时临时开启
 *             sampling-rate: 0.1                # 高并发环境建议 0.05 - 0.3 之间
 *           performance:
 *             enabled: true                     # 开启性能统计
 *             record-processing-time: true
 *             record-polling-time: true
 *             record-publishing-time: true
 *           error-monitoring:
 *             enabled: true
 *             classify-by-type: true            # 可用于按错误类型设定告警
 *             record-stack-trace: false         # 仅在定位问题时临时开启
 *           business-monitoring:
 *             enabled: true                     # 业务计数推荐开启
 *             record-message-count: true
 *             record-retry-count: true
 *             record-ack-count: true
 * </pre>
 *
 * <p><strong>常见效果与配合方式：</strong>
 * <ul>
 *   <li>结合 Spring Boot Actuator：通过 /actuator/metrics 查询具体指标；
 *       例如 redis.stream.processing.duration 按标签（stream=...）过滤。</li>
 *   <li>结合 Prometheus/Grafana：将 Micrometer 指标导出，构建告警（如 P95 处理耗时、
 *       积压数量、错误率）。</li>
 *   <li>采样率（sampling-rate）为 0.0 - 1.0：值越大样本越多。高 QPS 建议 0.05 - 0.3；
 *       低 QPS 或压测验证阶段可调高至 1.0。</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-09-15 16:46:38
 */
@Data
@ConfigurationProperties(prefix = "platform.cache.redis.stream.monitoring")
public class RedisStreamMonitoringProperties {

    /** 是否启用 Redis Stream 监控（总开关） */
    private boolean enabled = false;

    /** 健康检查配置 */
    private HealthCheck healthCheck = new HealthCheck();

    /** 指标采集配置 */
    private Metrics metrics = new Metrics();

    /** 性能指标配置 */
    private Performance performance = new Performance();

    /** 错误监控配置 */
    private ErrorMonitoring errorMonitoring = new ErrorMonitoring();

    /** 业务监控配置 */
    private BusinessMonitoring businessMonitoring = new BusinessMonitoring();

    /**
     * 健康检查配置
     * <p>用于控制健康检查频率、超时与是否启用。健康检查的结果将汇总到 /actuator/health
     * 以及自定义健康指示器（如 redisStream）。
     * <p><strong>生产建议：</strong>开启（enabled=true），interval=30s - 60s，timeout=3s - 5s。
     */
    @Data
    public static class HealthCheck {
        /**
         * 是否启用健康检查
         */
        private boolean enabled = false;
        /**
         * 健康检查间隔时间
         * <p>间隔越短越及时，但也会增加访问后端（如 Redis）的频率。
         */
        private Duration interval = Duration.ofSeconds(30);
        /**
         * 单次检查超时时间
         * <p>防止健康检查长时间阻塞，建议 3s - 5s。
         */
        private Duration timeout = Duration.ofSeconds(5);
    }

    /**
     * 指标采集配置
     * <p>控制 Micrometer 指标是否上报、是否开启直方图/分位数，以及采样率。
     * <ul>
     *   <li><strong>enabled</strong>：是否上报指标。</li>
     *   <li><strong>detailed</strong>：是否开启直方图与分位数（P50、P95、P99）。对内存与 CPU 有额外开销。</li>
     *   <li><strong>samplingRate</strong>：0.0 - 1.0，控制样本比例（与业务侧采样逻辑共同作用）。</li>
     * </ul>
     * <p><strong>生产建议：</strong>enabled=true，detailed=false（问题定位时临时开启），
     * samplingRate=0.05 - 0.3（高 QPS），或 1.0（低 QPS/压测阶段）。
     */
    @Data
    public static class Metrics {

        /** 是否上报指标 */
        private boolean enabled = false;

        /** 是否开启直方图与分位数 */
        private boolean detailed = true;

        /** 采样率（0.0 - 1.0） */
        private double samplingRate = 1.0;
    }

    /**
     * 性能指标配置
     * <p>控制是否记录处理、拉取、发布的耗时。与 {@link Metrics#samplingRate} 配合使用，
     * 可在高并发场景下有效控制开销。
     * <p><strong>生产建议：</strong>enabled=true；三项耗时均可开启；结合 samplingRate 控制样本量。
     */
    @Data
    public static class Performance {

        /** 是否启用性能统计 */
        private boolean enabled = false;

        /** 是否记录处理耗时 */
        private boolean recordProcessingTime = true;

        /** 是否记录拉取耗时 */
        private boolean recordPollingTime = true;

        /** 是否记录发布耗时 */
        private boolean recordPublishingTime = true;
    }

    /**
     * 错误监控配置
     * <p>控制是否采集错误、是否按照错误类型分类、是否记录堆栈。
     * <ul>
     *   <li><strong>enabled</strong>：是否启用错误指标采集（如总错误数、分类错误数）。</li>
     *   <li><strong>classifyByType</strong>：按异常类型分类，便于建立差异化告警。</li>
     *   <li><strong>recordStackTrace</strong>：记录堆栈，开销较大，仅在定位问题时临时开启。</li>
     * </ul>
     * <p><strong>生产建议：</strong>enabled=true，classifyByType=true，recordStackTrace=false。
     */
    @Data
    public static class ErrorMonitoring {

        /** 是否启用错误指标采集 */
        private boolean enabled = false;

        /** 是否按异常类型分类 */
        private boolean classifyByType = true;

        /** 是否记录堆栈（开销较大） */
        private boolean recordStackTrace = false;
    }

    /**
     * 业务监控配置
     * <p>用于统计与业务相关的计数，如发布/消费/确认/重试等。通常开销较低，
     * 可用于构建核心业务面板与 SLO/SLA 观测。
     * <p><strong>生产建议：</strong>整体开启；在极端性能敏感场景可仅开启关键计数。
     */
    @Data
    public static class BusinessMonitoring {

        /** 是否启用业务监控 */
        private boolean enabled = false;

        /** 是否记录消息计数 */
        private boolean recordMessageCount = true;

        /** 是否记录重试计数 */
        private boolean recordRetryCount = true;

        /** 是否记录确认计数 */
        private boolean recordAckCount = true;
    }
}


