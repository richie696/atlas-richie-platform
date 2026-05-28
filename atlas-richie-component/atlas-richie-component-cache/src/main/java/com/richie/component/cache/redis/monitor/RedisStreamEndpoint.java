package com.richie.component.cache.redis.monitor;

import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import com.richie.component.cache.redis.config.monitor.RedisStreamMonitoringProperties;
import com.richie.component.cache.redis.stream.RedisStreamControlBus;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis Stream 自定义管理端点
 *
 * <p>提供 Redis Stream 专用的管理功能，包括：
 * <ul>
 *   <li><strong>状态查询</strong>：查看 Stream 状态、消费者组信息、拉取器状态</li>
 *   <li><strong>指标查询</strong>：查看各种监控指标和统计数据</li>
 *   <li><strong>诊断工具</strong>：提供故障诊断和性能分析工具</li>
 * </ul>
 *
 * <p><strong>已支持端点路径：</strong>
 * <pre>{@code
 * # 总览状态（含健康与指标摘要）
 * GET /actuator/redis-stream
 *
 * # 特定 Stream 详情（含消费者组与拉取器状态、可选指标）
 * GET /actuator/redis-stream/{streamKey}
 *
 * # 特定 Stream 的消费者组信息
 * GET /actuator/redis-stream/{streamKey}/groups
 *
 * # 指标：摘要
 * GET /actuator/redis-stream/metrics/summary
 *
 * # 指标：分项（业务/性能/系统/错误/积压）
 * GET /actuator/redis-stream/metrics/business
 * GET /actuator/redis-stream/metrics/performance
 * GET /actuator/redis-stream/metrics/system
 * GET /actuator/redis-stream/metrics/errors
 * GET /actuator/redis-stream/metrics/backlog
 *
 * # 刷新健康检查（支持 GET 与 POST）
 * GET  /actuator/redis-stream/health/refresh
 * }</pre>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-09-15
 */
@Slf4j
@Component
@Endpoint(id = "redis-stream")
@RequiredArgsConstructor
@ConditionalOnClass({MeterRegistry.class})
public class RedisStreamEndpoint {

    /** Redis 模板（JSON 序列化） */
    @Qualifier("jsonTemplate")
    private final MultiRedisTemplate<Object> redisTemplate;

    /** Stream 指标 */
    private final RedisStreamMetrics metrics;

    /** 指标注册表 */
    private final MeterRegistry meterRegistry;

    /** 健康检查指示器 */
    private final RedisStreamHealthIndicator healthIndicator;

    /** 监控配置 */
    private final RedisStreamMonitoringProperties properties;

    /** 积压监控器 */
    private final RedisStreamBacklogMonitor backlogMonitor;

    /**
     * 获取 Redis Stream 整体状态
     *
     * @return 整体状态信息
     */
    @ReadOperation
    public Map<String, Object> getStatus() {
        log.debug("获取 Redis Stream 整体状态");

        Map<String, Object> status = new HashMap<>();

        try {
            // 基本信息
            status.put("timestamp", Instant.now().toString());
            status.put("status", "UP");

            // 健康检查状态
            status.put("health", getHealthStatus());

            // 监控指标（按配置控制是否返回详细指标块）
            if (properties.getMetrics().isEnabled()) {
                status.put("metrics", getMetricsSummary());
            }

            // 系统信息
            status.put("system", getSystemInfo());

        } catch (Exception e) {
            log.error("获取 Redis Stream 状态失败", e);
            status.put("error", e.getMessage());
            status.put("status", "ERROR");
        }

        return status;
    }

    /**
     * 获取特定 Stream 的详细信息
     *
     * @param streamKey Stream 键名
     * @return Stream 详细信息
     */
    @ReadOperation
    public Map<String, Object> getStreamInfo(@Selector String streamKey) {
        log.debug("获取 Stream 详细信息: streamKey={}", streamKey);

        Map<String, Object> info = new HashMap<>();

        try {
            // Stream 基本信息
            StreamInfo.XInfoStream streamInfo = redisTemplate.opsForStream().info(streamKey);
            //noinspection ConstantValue
            if (streamInfo != null) {
                info.put("streamKey", streamKey);
                info.put("length", streamInfo.streamLength());
                info.put("radixTreeKeys", streamInfo.radixTreeKeySize());
                info.put("radixTreeNodes", streamInfo.radixTreeNodesSize());
                info.put("groups", streamInfo.groupCount());
                info.put("lastGeneratedId", streamInfo.lastGeneratedId());
                info.put("firstEntry", streamInfo.firstEntryId());
                info.put("lastEntry", streamInfo.lastEntryId());
            } else {
                info.put("error", "Stream 不存在");
            }

            // 消费者组信息
            info.put("consumerGroups", getConsumerGroups(streamKey));

            // 拉取器状态
            info.put("pollers", getStreamPollers(streamKey));

            if (properties.getMetrics().isEnabled()) {
                info.put("metrics", getStreamMetrics(streamKey));
            }

        } catch (Exception e) {
            log.error("获取 Stream 信息失败: streamKey={}", streamKey, e);
            info.put("error", e.getMessage());
        }

        return info;
    }

    /**
     * 双段路径读操作（避免重复映射）：
     * - /actuator/redis-stream/{streamKey}/groups → 消费者组
     * - /actuator/redis-stream/metrics/summary → 指标汇总
     *
     * @param seg1 路径第一段（如 metrics、health 或 streamKey）
     * @param seg2 路径第二段（如 summary、groups 等）
     * @return 对应路径的响应数据
     */
    @ReadOperation
    public Map<String, Object> readTwoSegment(@Selector String seg1, @Selector String seg2) {
        // metrics/*
        if ("metrics".equals(seg1)) {
            return switch (seg2) {
                case "summary" -> getMetricsSummary();
                case "business" -> getBusinessMetrics();
                case "performance" -> getPerformanceMetrics();
                case "system" -> getSystemMetrics();
                case "errors" -> getErrorMetrics();
                case "backlog" -> getBacklogMetrics();
                default -> Map.of(
                        "error", "unsupported metrics section",
                        "path", "metrics/%s".formatted(seg2)
                );
            };
        }
        // health/refresh（提供 GET 方式的便捷刷新）
        if ("health".equals(seg1) && "refresh".equals(seg2)) {
            return refreshHealth();
        }
        // {streamKey}/groups
        if ("groups".equals(seg2)) {
            return getConsumerGroups(seg1);
        }
        return Map.of(
                "error", "unsupported path",
                "path", "%s/%s".formatted(seg1, seg2)
        );
    }

    /**
     * 刷新健康检查
     *
     * @return 刷新结果（status、timestamp 或 error）
     */
    @WriteOperation
    public Map<String, Object> refreshHealth() {
        try {
            healthIndicator.refreshHealthCheck();
            return Map.of(
                    "status", "OK",
                    "timestamp", Instant.now().toString()
            );
        } catch (Exception e) {
            log.error("刷新健康检查失败", e);
            return Map.of(
                    "status", "ERROR",
                    "error", e.getMessage()
            );
        }
    }


    // ==================== 私有方法 ====================

    /**
     * 获取健康状态
     *
     * @return 健康状态与详情
     */
    private Map<String, Object> getHealthStatus() {
        try {
            // 直接调用健康检查指示器
            var healthResult = healthIndicator.health();
            return Map.of(
                    "status", healthResult.getStatus().getCode(),
                    "details", healthResult.getDetails()
            );
        } catch (Exception e) {
            log.error("获取健康状态失败", e);
            return Map.of(
                    "status", "ERROR",
                    "error", e.getMessage()
            );
        }
    }

    /**
     * 获取指标摘要
     *
     * @return 业务/性能/系统/错误等指标汇总
     */
    private Map<String, Object> getMetricsSummary() {
        Map<String, Object> summary = new HashMap<>();

        try {
            // 使用 RedisStreamMetrics 获取实际指标数据
            if (properties.getBusinessMonitoring().isEnabled()) {
                summary.put("business", Map.of(
                        "messagesPublished", Map.of(
                                "value", metrics.getMessagesPublishedCount(),
                                "desc", "已发布消息总数"
                        ),
                        "messagesConsumed", Map.of(
                                "value", metrics.getMessagesConsumedCount(),
                                "desc", "已消费消息总数"
                        ),
                        "messagesAcknowledged", Map.of(
                                "value", metrics.getMessagesAcknowledgedCount(),
                                "desc", "已确认(ACK)消息总数"
                        ),
                        "messagesFailed", Map.of(
                                "value", metrics.getMessagesFailedCount(),
                                "desc", "处理失败消息总数"
                        ),
                        "messagesRetried", Map.of(
                                "value", metrics.getMessagesRetriedCount(),
                                "desc", "业务侧自定义重试次数（如有）"
                        )
                ));
            }

            if (properties.getPerformance().isEnabled()) {
                summary.put("performance", Map.of(
                        "processingDuration", Map.of(
                                "value", metrics.getProcessingDurationStats(),
                                "desc", "消息处理耗时分布统计"
                        ),
                        "pollingDuration", Map.of(
                                "value", metrics.getPollingDurationStats(),
                                "desc", "拉取(poll)耗时分布统计"
                        ),
                        "publishingDuration", Map.of(
                                "value", metrics.getPublishingDurationStats(),
                                "desc", "发布(publish)耗时分布统计"
                        )
                ));
            }

            if (properties.getErrorMonitoring().isEnabled()) {
                summary.put("errors", Map.of(
                        "totalErrors", Map.of(
                                "value", metrics.getTotalErrorsCount(),
                                "desc", "错误总次数"
                        ),
                        "timeoutErrors", Map.of(
                                "value", metrics.getTimeoutErrorsCount(),
                                "desc", "网络/处理超时错误次数"
                        ),
                        "connectionErrors", Map.of(
                                "value", metrics.getConnectionErrorsCount(),
                                "desc", "Redis 连接类错误次数"
                        ),
                        "serializationErrors", Map.of(
                                "value", metrics.getSerializationErrorsCount(),
                                "desc", "序列化/反序列化错误次数"
                        )
                ));
            }

            // 系统指标（始终显示）
            summary.put("system", Map.of(
                    "activeConsumers", Map.of(
                            "value", metrics.getActiveConsumersCount(),
                            "desc", "活跃消费者数量（应用内统计）"
                    ),
                    "activePollers", Map.of(
                            "value", metrics.getActivePollersCount(),
                            "desc", "活跃拉取器数量"
                    ),
                    "messageBacklog", Map.of(
                            "value", metrics.getMessageBacklogCount(),
                            "desc", "消息积压估算（全局）"
                    ),
                    "activeConnections", Map.of(
                            "value", metrics.getActiveConnectionsCount(),
                            "desc", "活跃 Redis 连接数（指标来源于连接池/客户端）"
                    )
            ));

        } catch (Exception e) {
            summary.put("error", e.getMessage());
        }

        return summary;
    }

    /**
     * 获取系统信息
     *
     * @return 时间戳与 JVM 信息
     */
    private Map<String, Object> getSystemInfo() {
        Map<String, Object> system = new HashMap<>();

        system.put("timestamp", Instant.now().toString());
        system.put("jvm", Map.of(
                "availableProcessors", Runtime.getRuntime().availableProcessors(),
                "totalMemory", Runtime.getRuntime().totalMemory(),
                "freeMemory", Runtime.getRuntime().freeMemory(),
                "maxMemory", Runtime.getRuntime().maxMemory()
        ));

        return system;
    }

    /**
     * 获取消费者组信息
     *
     * @param streamKey Stream 键名
     * @return 消费者组列表及数量
     */
    private Map<String, Object> getConsumerGroups(String streamKey) {
        log.debug("获取消费者组信息: streamKey={}", streamKey);

        Map<String, Object> groups = new HashMap<>();

        try {
            StreamInfo.XInfoGroups groupInfo = redisTemplate.opsForStream().groups(streamKey);
            //noinspection ConstantValue
            if (groupInfo != null) {
                groups.put("count", groupInfo.size());
                groups.put("groups", groupInfo.stream()
                        .map(group -> Map.of(
                                "name", Map.of(
                                        "value", group.groupName(),
                                        "desc", "消费者组名称"
                                ),
                                "consumers", Map.of(
                                        "value", group.consumerCount(),
                                        "desc", "该组内活跃消费者数量"
                                ),
                                "pending", Map.of(
                                        "value", group.pendingCount(),
                                        "desc", "该组未确认(PENDING)的消息数量"
                                ),
                                "lastDeliveredId", Map.of(
                                        "value", group.lastDeliveredId(),
                                        "desc", "该组最后一次投递给消费者的记录ID"
                                )
                        ))
                        .collect(Collectors.toList()));
            } else {
                groups.put("count", 0);
                groups.put("groups", Collections.emptyList());
            }

        } catch (Exception e) {
            log.error("获取消费者组信息失败: streamKey={}", streamKey, e);
            groups.put("error", e.getMessage());
        }

        return groups;
    }

    /**
     * 获取特定 Stream 的拉取器
     *
     * @param streamKey Stream 键名
     * @return 拉取器状态列表
     */
    private List<Map<String, Object>> getStreamPollers(String streamKey) {
        try {
            String correlationId = UUID.randomUUID().toString();
            CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
            var sub = RedisStreamControlBus.controlFlow()
                    .filter(evt -> evt instanceof RedisStreamControlBus.PollerStatusResponse r && r.correlationId().equals(correlationId))
                    .take(1)
                    .subscribe(evt -> future.complete(((RedisStreamControlBus.PollerStatusResponse) evt).snapshot()), future::completeExceptionally);
            RedisStreamControlBus.publish(new RedisStreamControlBus.PollerStatusQuery(correlationId));

            Map<String, Object> snapshot = future.get(1500, TimeUnit.MILLISECONDS);
            sub.dispose();

            return snapshot.values().stream()
                    .filter(v -> v instanceof Map<?, ?> m && streamKey.equals(m.get("streamKey")))
                    .map(v -> {
                        Map<?, ?> m = (Map<?, ?>) v;
                        return Map.of(
                                "group", m.get("group"),
                                "consumer", m.get("consumer"),
                                "active", m.get("active"),
                                "startTime", m.get("startTime"),
                                "lastActivity", m.get("lastActivity"),
                                "messageCount", m.get("messageCount"),
                                "errorCount", m.get("errorCount")
                        );
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("获取 Stream 拉取器列表失败: streamKey={}, err={}", streamKey, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取特定 Stream 的指标
     *
     * @param streamKey Stream 键名
     * @return 该 Stream 的发布/消费/失败计数等
     */
    private Map<String, Object> getStreamMetrics(String streamKey) {
        Map<String, Object> streamMetrics = new HashMap<>();

        try {
            streamMetrics.put("messagesPublished", getCounterValue("redis.stream.messages.published", "stream", streamKey));
            streamMetrics.put("messagesConsumed", getCounterValue("redis.stream.messages.consumed", "stream", streamKey));
            streamMetrics.put("messagesFailed", getCounterValue("redis.stream.messages.failed", "stream", streamKey));

        } catch (Exception e) {
            streamMetrics.put("error", e.getMessage());
        }

        return streamMetrics;
    }

    /**
     * 获取业务指标
     *
     * @return 业务指标 Map
     */
    private Map<String, Object> getBusinessMetrics() {
        return healthIndicator.getBusinessMetrics();
    }

    /**
     * 获取性能指标
     *
     * @return 性能指标 Map
     */
    private Map<String, Object> getPerformanceMetrics() {
        return healthIndicator.getPerformanceMetrics();
    }

    /**
     * 获取系统指标
     *
     * @return 系统指标 Map
     */
    private Map<String, Object> getSystemMetrics() {
        return healthIndicator.getSystemMetrics();
    }

    /**
     * 获取错误指标
     *
     * @return 错误指标 Map
     */
    private Map<String, Object> getErrorMetrics() {
        return healthIndicator.getErrorMetrics();
    }

    /**
     * 获取积压指标
     *
     * @return 积压统计 Map
     */
    private Map<String, Object> getBacklogMetrics() {
        try {
            // 手动触发积压检查
            backlogMonitor.refreshBacklog();

            // 获取积压统计信息
            return backlogMonitor.getBacklogStats();
        } catch (Exception e) {
            log.error("获取积压指标失败", e);
            return Map.of(
                    "error", e.getMessage(),
                    "totalBacklog", 0,
                    "monitoredStreams", 0
            );
        }
    }

    /**
     * 获取计数器值
     *
     * @param name  指标名称
     * @param tags  指标标签（如 stream、group）
     * @return 计数值，不存在或异常时为 0.0
     */
    private double getCounterValue(String name, String... tags) {
        try {
            var counter = meterRegistry.find(name).tags(tags).counter();
            return counter != null ? counter.count() : 0.0;
        } catch (Exception e) {
            log.debug("获取计数器值失败: name={}, tags={}, error={}", name, Arrays.toString(tags), e.getMessage());
            return 0.0;
        }
    }

}
