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
package com.richie.component.redis.streammq.monitor;

import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import com.richie.component.redis.streammq.config.monitor.RedisStreamMonitoringProperties;
import com.richie.component.redis.streammq.stream.RedisStreamReactor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis Stream 健康检查指示器
 *
 * <p>负责对 Redis Stream 相关组件进行健康检查，并输出可读性强的详情，用于运维与排障。
 *
 * <p>主要功能：
 * <ul>
 *   <li>连接可达性与基本读写能力检查</li>
 *   <li>注册的 Stream 与消费者组状态检查（按实际注册与拉取器快照动态检测）</li>
 *   <li>业务/性能/错误/系统等多维健康指标聚合</li>
 *   <li>提供健康刷新能力，支持端点触发</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-09-16
 */
@Slf4j
@Component
@ConditionalOnClass({HealthIndicator.class, MeterRegistry.class})
public class RedisStreamHealthIndicator implements HealthIndicator {

    /** Redis 模板（JSON 序列化） */
    @Qualifier("jsonTemplate")
    private final MultiRedisTemplate<Object> redisTemplate;

    /** Stream 指标收集 */
    private final RedisStreamMetrics metrics;

    /** 指标注册表 */
    private final MeterRegistry meterRegistry;

    /** Stream 反应器 */
    private final RedisStreamReactor reactor;

    /** 监控配置 */
    private final RedisStreamMonitoringProperties properties;

    /** 健康检查状态缓存（streamKey -> 状态） */
    private final Map<String, HealthStatus> healthStatusCache = new ConcurrentHashMap<>();

    /** 上次健康检查时间戳 */
    private final AtomicLong lastCheckTime = new AtomicLong(0);

    /** 健康检查耗时计时器 */
    private final Timer healthCheckDuration;

    /** 健康检查成功计数 */
    private final Counter healthCheckSuccess;

    /** 健康检查失败计数 */
    private final Counter healthCheckFailure;

    /** Redis 连接错误计数 */
    private final Counter redisConnectionErrors;

    /** Stream 可用性错误计数 */
    private final Counter streamAvailabilityErrors;

    /** 消费者组错误计数 */
    private final Counter consumerGroupErrors;

    /** 拉取器错误计数 */
    private final Counter pollerErrors;

    /** 业务健康错误计数 */
    private final Counter businessHealthErrors;

    /**
     * 构造健康检查器。
     *
     * @param redisTemplate Redis 模板
     * @param metrics       Stream 指标
     * @param reactor       Stream 反应器
     * @param meterRegistry 指标注册表
     * @param properties    监控配置
     */
    public RedisStreamHealthIndicator(MultiRedisTemplate<Object> redisTemplate,
                                    RedisStreamMetrics metrics,
                                    RedisStreamReactor reactor,
                                    MeterRegistry meterRegistry,
                                    RedisStreamMonitoringProperties properties) {
        this.redisTemplate = redisTemplate;
        this.metrics = metrics;
        this.reactor = reactor;
        this.meterRegistry = meterRegistry;
        this.properties = properties;

        // 初始化健康检查指标
        this.healthCheckDuration = Timer.builder("redis.stream.health.check.duration")
                .description("Redis Stream 健康检查耗时")
                .register(meterRegistry);

        this.healthCheckSuccess = Counter.builder("redis.stream.health.check.success")
                .description("Redis Stream 健康检查成功次数")
                .register(meterRegistry);

        this.healthCheckFailure = Counter.builder("redis.stream.health.check.failure")
                .description("Redis Stream 健康检查失败次数")
                .register(meterRegistry);

        this.redisConnectionErrors = Counter.builder("redis.stream.health.redis.errors")
                .description("Redis 连接健康检查错误次数")
                .register(meterRegistry);

        this.streamAvailabilityErrors = Counter.builder("redis.stream.health.stream.errors")
                .description("Stream 可用性健康检查错误次数")
                .register(meterRegistry);

        this.consumerGroupErrors = Counter.builder("redis.stream.health.consumer.group.errors")
                .description("消费者组健康检查错误次数")
                .register(meterRegistry);

        this.pollerErrors = Counter.builder("redis.stream.health.poller.errors")
                .description("拉取器健康检查错误次数")
                .register(meterRegistry);

        this.businessHealthErrors = Counter.builder("redis.stream.health.business.errors")
                .description("业务健康检查错误次数")
                .register(meterRegistry);
    }

    /**
     * 健康状态接口
     */
    public interface HealthStatus {

        /**
         * 是否健康
         * @return 是否健康
         */
        boolean isHealthy();

        /**
         * 描述信息
         * @return 描述信息
         */
        String getMessage();

        /**
         * 响应耗时（毫秒）
         * @return 响应耗时（毫秒）
         */
        long getResponseTime();
    }

    /**
     * 健康状态枚举
     */
    public enum HealthLevel {
        /**
         *  健康
         */
        UP,
        /**
         * 不健康
         */
        DOWN,
        /**
         * 降级
         */
        DEGRADED
    }

    /**
     * 健康检查结果
     *
     * @param healthy     是否健康
     * @param message     描述信息
     * @param responseTime 响应耗时（毫秒）
     * @param details     明细数据
     */
    public record HealthCheckResult(boolean healthy, String message, long responseTime, Map<String, Object> details) {
    }

    @Override
    public Health health() {
        try {
            Timer.Sample sample = Timer.start(meterRegistry);
            long currentTime = System.currentTimeMillis();

            // 检查是否需要刷新缓存
            long intervalMs = properties.getHealthCheck().getInterval().toMillis();
            if (currentTime - lastCheckTime.get() > intervalMs) {
                performHealthChecks();
                lastCheckTime.set(currentTime);
            }

            // 构建健康状态
            Health result = buildHealthStatus();
            sample.stop(healthCheckDuration);
            healthCheckSuccess.increment();
            return result;

        } catch (Exception e) {
            healthCheckFailure.increment();
            log.error("Redis Stream 健康检查异常", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("timestamp", Instant.now().toString())
                    .build();
        }
    }

    /**
     * 执行健康检查
     */
    private void performHealthChecks() {
        log.debug("开始执行 Redis Stream 健康检查");

        // 1. Redis 连接健康检查
        HealthCheckResult redisHealth = checkRedisConnection();
        healthStatusCache.put("redis", new HealthStatus() {
            @Override
            public boolean isHealthy() {
                return redisHealth.healthy();
            }

            @Override
            public String getMessage() {
                return redisHealth.message();
            }

            @Override
            public long getResponseTime() {
                return redisHealth.responseTime();
            }
        });

        // 2. Stream 可用性检查
        HealthCheckResult streamHealth = checkStreamAvailability();
        healthStatusCache.put("stream", new HealthStatus() {
            @Override
            public boolean isHealthy() {
                return streamHealth.healthy();
            }

            @Override
            public String getMessage() {
                return streamHealth.message();
            }

            @Override
            public long getResponseTime() {
                return streamHealth.responseTime();
            }
        });

        // 3. 消费者组状态检查
        HealthCheckResult consumerGroupHealth = checkConsumerGroupStatus();
        healthStatusCache.put("consumerGroup", new HealthStatus() {
            @Override
            public boolean isHealthy() {
                return consumerGroupHealth.healthy();
            }

            @Override
            public String getMessage() {
                return consumerGroupHealth.message();
            }

            @Override
            public long getResponseTime() {
                return consumerGroupHealth.responseTime();
            }
        });

        // 4. 拉取器状态检查
        HealthCheckResult pollerHealth = checkPollerStatus();
        healthStatusCache.put("poller", new HealthStatus() {
            @Override
            public boolean isHealthy() {
                return pollerHealth.healthy();
            }

            @Override
            public String getMessage() {
                return pollerHealth.message();
            }

            @Override
            public long getResponseTime() {
                return pollerHealth.responseTime();
            }
        });

        // 5. 业务健康检查
        HealthCheckResult businessHealth = checkBusinessHealth();
        healthStatusCache.put("business", new HealthStatus() {
            @Override
            public boolean isHealthy() {
                return businessHealth.healthy();
            }

            @Override
            public String getMessage() {
                return businessHealth.message();
            }

            @Override
            public long getResponseTime() {
                return businessHealth.responseTime();
            }
        });

        log.debug("Redis Stream 健康检查完成");
    }

    /**
     * 检查 Redis 连接健康
     * 使用多种方式检查，按优先级尝试：PING -> GET -> INFO
     *
     * @return 连接检查结果
     */
    private HealthCheckResult checkRedisConnection() {
        long startTime = System.currentTimeMillis();

        // 方案1：尝试使用 PING 命令
        try {
            String result = redisTemplate.execute(RedisConnection::ping);
            long responseTime = System.currentTimeMillis() - startTime;

            if ("PONG".equals(result)) {
                return new HealthCheckResult(true, "Redis 连接正常 (PING)", responseTime, Map.of(
                        "method", "PING",
                        "result", result,
                        "responseTime", "%dms".formatted(responseTime)
                ));
            }
        } catch (Exception e) {
            redisConnectionErrors.increment();
            log.debug("PING 命令失败，尝试备用方案: {}", e.getMessage());
        }

        // 方案2：尝试使用简单的 GET 操作
        try {
            String testKey = "health_check_%d".formatted(System.currentTimeMillis());
            redisTemplate.opsForValue().set(testKey, "test", java.time.Duration.ofSeconds(1));
            String result = redisTemplate.opsForValue().get(testKey, 0, 1);
            redisTemplate.delete(testKey);

            long responseTime = System.currentTimeMillis() - startTime;

            if ("test".equals(result)) {
                return new HealthCheckResult(true, "Redis 连接正常 (GET)", responseTime, Map.of(
                        "method", "GET",
                        "result", "success",
                        "responseTime", "%dms".formatted(responseTime)
                ));
            }
        } catch (Exception e) {
            redisConnectionErrors.increment();
            log.debug("GET 操作失败，尝试最后备用方案: {}", e.getMessage());
        }

        // 方案3：尝试使用 INFO 命令
        try {
            String info = redisTemplate.execute((RedisConnection connection) -> {
                Properties properties = connection.serverCommands().info();

                // 检查 properties 是否为 null
                if (properties == null) {
                    log.debug("Redis INFO 命令返回 null，可能是权限不足或服务器配置问题");
                    return null;
                }

                StringWriter writer = new StringWriter();
                try {
                    properties.store(writer, null);
                    return writer.toString();
                } catch (IOException e) {
                    // 如果 store 失败，回退到 toString()
                    return properties.toString();
                }
            });
            long responseTime = System.currentTimeMillis() - startTime;

            if (info != null && !info.isEmpty()) {
                return new HealthCheckResult(true, "Redis 连接正常 (INFO)", responseTime, Map.of(
                        "method", "INFO",
                        "result", "success",
                        "responseTime", "%dms".formatted(responseTime)
                ));
            } else {
                log.debug("Redis INFO 命令返回空结果");
            }
        } catch (Exception e) {
            redisConnectionErrors.increment();
            log.debug("INFO 命令失败: {}", e.getMessage());
        }

        // 所有方案都失败
        long responseTime = System.currentTimeMillis() - startTime;
        return new HealthCheckResult(false, "Redis 连接失败: 所有检查方法都不可用", responseTime, Map.of(
                "error", "所有检查方法都失败",
                "responseTime", "%dms".formatted(responseTime)
        ));
    }

    /**
     * 检查 Stream 可用性
     *
     * @return Stream 可用性检查结果
     */
    private HealthCheckResult checkStreamAvailability() {
        long startTime = System.currentTimeMillis();

        try {
            // 从实际的拉取器状态中获取 Stream 信息
            Map<String, Object> pollerSnapshot = reactor.getPollerStatusSnapshot();

            if (pollerSnapshot.isEmpty()) {
                // 如果没有活跃的拉取器，进行基本的 Stream 功能测试
                return performBasicStreamTest(startTime);
            }

            // 检查每个活跃拉取器对应的 Stream 状态
            int totalStreams = 0;
            int healthyStreams = 0;
            Map<String, Object> streamDetails = new HashMap<>();

            for (Map.Entry<String, Object> entry : pollerSnapshot.entrySet()) {
                String pollerKey = entry.getKey();
                Object statusObj = entry.getValue();

                if (statusObj instanceof Map<?, ?> status) {
                    String streamKey = (String) status.get("streamKey");

                    if (streamKey != null) {
                        totalStreams++;
                        try {
                            // 检查该 Stream 的基本信息
                            StreamInfo.XInfoStream streamInfo = redisTemplate.opsForStream().info(streamKey);
                            if (streamInfo != null) {
                                healthyStreams++;

                                streamDetails.put(streamKey, Map.of(
                                        "length", streamInfo.streamLength(),
                                        "groups", streamInfo.groupCount(),
                                        "lastGeneratedId", streamInfo.lastGeneratedId(),
                                        "firstEntry", streamInfo.firstEntryId(),
                                        "lastEntry", streamInfo.lastEntryId(),
                                        "status", "healthy"
                                ));
                            } else {
                                streamDetails.put(streamKey, Map.of(
                                        "status", "empty",
                                        "message", "Stream 存在但无数据"
                                ));
                                healthyStreams++; // 空 Stream 也算正常
                            }
                        } catch (Exception streamEx) {
                            // 单个 Stream 检查失败
                            streamDetails.put(streamKey, Map.of(
                                    "status", "error",
                                    "error", streamEx.getMessage()
                            ));
                        }
                    }
                }
            }

            long responseTime = System.currentTimeMillis() - startTime;

            if (healthyStreams == totalStreams && totalStreams > 0) {
                return new HealthCheckResult(true, "所有 Stream 状态正常", responseTime, Map.of(
                        "totalStreams", totalStreams,
                        "healthyStreams", healthyStreams,
                        "streamDetails", streamDetails,
                        "responseTime", "%dms".formatted(responseTime)
                ));
            } else if (healthyStreams > 0) {
                return new HealthCheckResult(true, "部分 Stream 状态正常", responseTime, Map.of(
                        "totalStreams", totalStreams,
                        "healthyStreams", healthyStreams,
                        "unhealthyStreams", totalStreams - healthyStreams,
                        "streamDetails", streamDetails,
                        "responseTime", "%dms".formatted(responseTime)
                ));
            } else {
                return new HealthCheckResult(false, "所有 Stream 检查失败", responseTime, Map.of(
                        "totalStreams", totalStreams,
                        "healthyStreams", 0,
                        "streamDetails", streamDetails,
                        "responseTime", "%dms".formatted(responseTime)
                ));
            }

        } catch (Exception e) {
            streamAvailabilityErrors.increment();
            long responseTime = System.currentTimeMillis() - startTime;
            return new HealthCheckResult(false, "Redis Stream 检查失败: %s".formatted(e.getMessage()), responseTime, Map.of(
                    "error", e.getMessage(),
                    "responseTime", "%dms".formatted(responseTime)
            ));
        }
    }

    /**
     * 执行基本的 Stream 功能测试（当没有活跃拉取器时）
     *
     * @param startTime 健康检查开始时间戳（用于计算 responseTime）
     * @return 基本功能测试结果
     */
    private HealthCheckResult performBasicStreamTest(long startTime) {
        try {
            // 使用一个临时的测试 Stream 来验证功能
            String testStream = "health-check-test-stream";

            // 添加一个测试消息
            redisTemplate.opsForStream().add(testStream, Map.of("test", "health-check"));

            // 获取 Stream 信息
            StreamInfo.XInfoStream streamInfo = redisTemplate.opsForStream().info(testStream);

            // 删除测试 Stream
            redisTemplate.delete(testStream);

            long responseTime = System.currentTimeMillis() - startTime;

            if (streamInfo != null) {
                return new HealthCheckResult(true, "Stream 功能正常（无活跃拉取器）", responseTime, Map.of(
                        "testStream", testStream,
                        "length", streamInfo.streamLength(),
                        "message", "Stream 基本功能正常，但当前没有活跃的拉取器",
                        "responseTime", "%dms".formatted(responseTime)
                ));
            } else {
                return new HealthCheckResult(true, "Stream 功能正常（无数据）", responseTime, Map.of(
                        "testStream", testStream,
                        "message", "Stream 操作成功，但无数据",
                        "responseTime", "%dms".formatted(responseTime)
                ));
            }

        } catch (Exception streamEx) {
            // 如果 Stream 操作失败，尝试基本的 Redis 连接检查
            try {
                redisTemplate.opsForValue().set("health-check-test", "ok", Duration.ofSeconds(1));
                String result = redisTemplate.opsForValue().get("health-check-test", 0, 1);
                redisTemplate.delete("health-check-test");

                long responseTime = System.currentTimeMillis() - startTime;

                if ("ok".equals(result)) {
                    return new HealthCheckResult(true, "Redis 连接正常，Stream 功能受限", responseTime, Map.of(
                            "message", "Redis 基本操作正常，但 Stream 功能可能不可用",
                            "streamError", streamEx.getMessage(),
                            "responseTime", "%dms".formatted(responseTime)
                    ));
                } else {
                    throw streamEx; // 重新抛出异常
                }
            } catch (Exception e) {
                throw streamEx; // 重新抛出原始异常
            }
        }
    }

    /**
     * 检查消费者组状态
     *
     * @return 消费者组状态检查结果
     */
    private HealthCheckResult checkConsumerGroupStatus() {
        long startTime = System.currentTimeMillis();

        try {
            // 从实际的拉取器状态中获取 Stream 信息
            Map<String, Object> pollerSnapshot = reactor.getPollerStatusSnapshot();

            if (pollerSnapshot.isEmpty()) {
                long responseTime = System.currentTimeMillis() - startTime;
                return new HealthCheckResult(true, "无活跃拉取器（正常）", responseTime, Map.of(
                        "activePollers", 0,
                        "message", "当前没有活跃的拉取器，这是正常状态",
                        "responseTime", "%dms".formatted(responseTime)
                ));
            }

            // 检查每个活跃拉取器对应的 Stream 和消费者组状态
            int totalGroups = 0;
            int healthyStreams = 0;
            Map<String, Object> streamDetails = new HashMap<>();

            for (Map.Entry<String, Object> entry : pollerSnapshot.entrySet()) {
                String pollerKey = entry.getKey();
                Object statusObj = entry.getValue();

                if (statusObj instanceof Map<?, ?> status) {
                    String streamKey = (String) status.get("streamKey");
                    String group = (String) status.get("group");

                    if (streamKey != null && group != null) {
                        try {
                            // 检查该 Stream 的消费者组状态
                            StreamInfo.XInfoGroups groups = redisTemplate.opsForStream().groups(streamKey);
                            if (groups != null) {
                                totalGroups += groups.size();
                                healthyStreams++;

                                streamDetails.put(streamKey, Map.of(
                                        "group", group,
                                        "groupCount", groups.size(),
                                        "groups", groups.stream()
                                                .map(g -> Map.of(
                                                        "name", g.groupName(),
                                                        "consumers", g.consumerCount(),
                                                        "pending", g.pendingCount()
                                                ))
                                                .toList()
                                ));
                            }
                        } catch (Exception streamEx) {
                            // 单个 Stream 检查失败，记录但不影响整体状态
                            streamDetails.put(streamKey, Map.of(
                                    "group", group,
                                    "error", streamEx.getMessage(),
                                    "status", "error"
                            ));
                        }
                    }
                }
            }

            long responseTime = System.currentTimeMillis() - startTime;

            if (healthyStreams > 0) {
                return new HealthCheckResult(true, "消费者组状态正常", responseTime, Map.of(
                        "activePollers", pollerSnapshot.size(),
                        "healthyStreams", healthyStreams,
                        "totalGroups", totalGroups,
                        "streamDetails", streamDetails,
                        "responseTime", "%dms".formatted(responseTime)
                ));
            } else {
                return new HealthCheckResult(false, "所有 Stream 检查失败", responseTime, Map.of(
                        "activePollers", pollerSnapshot.size(),
                        "healthyStreams", 0,
                        "streamDetails", streamDetails,
                        "responseTime", "%dms".formatted(responseTime)
                ));
            }

        } catch (Exception e) {
            consumerGroupErrors.increment();
            long responseTime = System.currentTimeMillis() - startTime;
            return new HealthCheckResult(false, "消费者组检查失败: %s".formatted(e.getMessage()), responseTime, Map.of(
                    "error", e.getMessage(),
                    "responseTime", "%dms".formatted(responseTime)
            ));
        }
    }

    /**
     * 检查拉取器状态
     *
     * @return 拉取器状态检查结果
     */
    private HealthCheckResult checkPollerStatus() {
        long startTime = System.currentTimeMillis();

        try {
            // 实际检查 RedisStreamReactor 的拉取器状态
            int active = reactor.getActivePollerCount();
            Map<String, Object> snapshot = reactor.getPollerStatusSnapshot();

            // 规则：无活跃拉取器 → WARN；最近活动超过阈值 → WARN
            boolean ok = active > 0;

            // 统计最近活跃超时的拉取器数量（阈值：60s）
            int timeoutCount = 0;
            long now = System.currentTimeMillis();
            for (Object v : snapshot.values()) {
                if (v instanceof Map<?, ?> m) {
                    Object last = m.get("lastActivity");
                    if (last instanceof String ts) {
                        try {
                            long lastEpochMs = Instant.parse(ts).toEpochMilli();
                            if (now - lastEpochMs > 60_000) {
                                timeoutCount++;
                            }
                        } catch (Exception ignore) {
                        }
                    }
                }
            }

            ok = ok && timeoutCount == 0;

            long responseTime = System.currentTimeMillis() - startTime;
            Map<String, Object> details = new HashMap<>();
            details.put("activePollers", active);
            details.put("timeoutPollers", timeoutCount);
            details.put("pollers", snapshot);
            details.put("responseTime", "%dms".formatted(responseTime));

            return new HealthCheckResult(ok, ok ? "拉取器状态正常" : "拉取器存在异常", responseTime, details);

        } catch (Exception e) {
            pollerErrors.increment();
            long responseTime = System.currentTimeMillis() - startTime;
            return new HealthCheckResult(false, "拉取器检查失败: %s".formatted(e.getMessage()), responseTime, Map.of(
                    "error", e.getMessage(),
                    "responseTime", "%dms".formatted(responseTime)
            ));
        }
    }

    /**
     * 检查业务健康
     *
     * @return 业务健康检查结果
     */
    private HealthCheckResult checkBusinessHealth() {
        long startTime = System.currentTimeMillis();

        try {
            // 基于实际指标数据检查业务健康
            Map<String, Object> businessMetrics = getBusinessHealthMetrics();

            long responseTime = System.currentTimeMillis() - startTime;

            return new HealthCheckResult(true, "业务健康正常", responseTime, businessMetrics);

        } catch (Exception e) {
            businessHealthErrors.increment();
            long responseTime = System.currentTimeMillis() - startTime;
            return new HealthCheckResult(false, "业务健康检查失败: " + e.getMessage(), responseTime, Map.of(
                    "error", e.getMessage(),
                    "responseTime", responseTime + "ms"
            ));
        }
    }

    /**
     * 获取业务健康指标
     *
     * @return 业务指标 Map
     */
    private Map<String, Object> getBusinessHealthMetrics() {
        Map<String, Object> result = new HashMap<>();

        try {
            if (properties.getBusinessMonitoring().isEnabled()) {
                Map<String, Object> business = new HashMap<>();
                business.put("messagesPublished", Map.of(
                        "value", metrics.getMessagesPublishedCount(),
                        "desc", "已发布消息总数"
                ));
                business.put("messagesConsumed", Map.of(
                        "value", metrics.getMessagesConsumedCount(),
                        "desc", "已消费消息总数"
                ));
                business.put("messagesAcknowledged", Map.of(
                        "value", metrics.getMessagesAcknowledgedCount(),
                        "desc", "已确认(ACK)消息总数"
                ));
                business.put("messagesFailed", Map.of(
                        "value", metrics.getMessagesFailedCount(),
                        "desc", "处理失败消息总数"
                ));
                business.put("messagesRetried", Map.of(
                        "value", metrics.getMessagesRetriedCount(),
                        "desc", "业务侧自定义重试次数（如有）"
                ));
                result.put("business", business);
            } else {
                result.put("business", Map.of(
                        "enabled", Map.of("value", false, "desc", "业务指标未启用")
                ));
            }

            if (properties.getPerformance().isEnabled()) {
                Map<String, Object> performance = new HashMap<>();
                performance.put("processingDuration", Map.of(
                        "value", metrics.getProcessingDurationStats(),
                        "desc", "消息处理耗时分布统计"
                ));
                performance.put("pollingDuration", Map.of(
                        "value", metrics.getPollingDurationStats(),
                        "desc", "拉取(poll)耗时分布统计"
                ));
                performance.put("publishingDuration", Map.of(
                        "value", metrics.getPublishingDurationStats(),
                        "desc", "发布(publish)耗时分布统计"
                ));
                result.put("performance", performance);
            } else {
                result.put("performance", Map.of(
                        "enabled", Map.of("value", false, "desc", "性能指标未启用")
                ));
            }

            if (properties.getErrorMonitoring().isEnabled()) {
                Map<String, Object> errors = new HashMap<>();
                errors.put("totalErrors", Map.of(
                        "value", metrics.getTotalErrorsCount(),
                        "desc", "错误总次数"
                ));
                errors.put("timeoutErrors", Map.of(
                        "value", metrics.getTimeoutErrorsCount(),
                        "desc", "网络/处理超时错误次数"
                ));
                errors.put("connectionErrors", Map.of(
                        "value", metrics.getConnectionErrorsCount(),
                        "desc", "Redis 连接类错误次数"
                ));
                errors.put("serializationErrors", Map.of(
                        "value", metrics.getSerializationErrorsCount(),
                        "desc", "序列化/反序列化错误次数"
                ));
                result.put("errors", errors);
            } else {
                result.put("errors", Map.of(
                        "enabled", Map.of("value", false, "desc", "错误指标未启用")
                ));
            }

        } catch (Exception e) {
            log.debug("获取业务健康指标失败: {}", e.getMessage());
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 获取业务指标（供外部调用）
     *
     * @return 业务指标 Map
     */
    public Map<String, Object> getBusinessMetrics() {
        Map<String, Object> business = new HashMap<>();

        if (properties.getBusinessMonitoring().isEnabled()) {
            business.put("messagesPublished", Map.of(
                    "value", metrics.getMessagesPublishedCount(),
                    "desc", "已发布消息总数"
            ));
            business.put("messagesConsumed", Map.of(
                    "value", metrics.getMessagesConsumedCount(),
                    "desc", "已消费消息总数"
            ));
            business.put("messagesAcknowledged", Map.of(
                    "value", metrics.getMessagesAcknowledgedCount(),
                    "desc", "已确认(ACK)消息总数"
            ));
            business.put("messagesFailed", Map.of(
                    "value", metrics.getMessagesFailedCount(),
                    "desc", "处理失败消息总数"
            ));
            business.put("messagesRetried", Map.of(
                    "value", metrics.getMessagesRetriedCount(),
                    "desc", "业务侧自定义重试次数（如有）"
            ));
        } else {
            business.put("enabled", false);
            business.put("message", "业务监控已禁用");
        }

        return business;
    }

    /**
     * 获取性能指标（供外部调用）
     *
     * @return 性能指标 Map
     */
    public Map<String, Object> getPerformanceMetrics() {
        Map<String, Object> performance = new HashMap<>();

        if (properties.getPerformance().isEnabled()) {
            performance.put("processingDuration", Map.of(
                    "value", metrics.getProcessingDurationStats(),
                    "desc", "消息处理耗时分布统计"
            ));
            performance.put("pollingDuration", Map.of(
                    "value", metrics.getPollingDurationStats(),
                    "desc", "拉取(poll)耗时分布统计"
            ));
            performance.put("publishingDuration", Map.of(
                    "value", metrics.getPublishingDurationStats(),
                    "desc", "发布(publish)耗时分布统计"
            ));
        } else {
            performance.put("enabled", false);
            performance.put("message", "性能监控已禁用");
        }

        return performance;
    }

    /**
     * 获取系统指标（供外部调用）
     *
     * @return 系统指标 Map
     */
    public Map<String, Object> getSystemMetrics() {
        Map<String, Object> system = new HashMap<>();

        system.put("activeConsumers", Map.of(
                "value", metrics.getActiveConsumersCount(),
                "desc", "活跃消费者数量（应用内统计）"
        ));
        system.put("activePollers", Map.of(
                "value", metrics.getActivePollersCount(),
                "desc", "活跃拉取器数量"
        ));
        system.put("messageBacklog", Map.of(
                "value", metrics.getMessageBacklogCount(),
                "desc", "消息积压估算（全局）"
        ));
        system.put("activeConnections", Map.of(
                "value", metrics.getActiveConnectionsCount(),
                "desc", "活跃 Redis 连接数（来源于连接池/客户端）"
        ));

        return system;
    }

    /**
     * 获取错误指标（供外部调用）
     * @return 错误指标
     */
    public Map<String, Object> getErrorMetrics() {
        Map<String, Object> errors = new HashMap<>();

        if (properties.getErrorMonitoring().isEnabled()) {
            errors.put("totalErrors", Map.of(
                    "value", metrics.getTotalErrorsCount(),
                    "desc", "错误总次数"
            ));
            errors.put("timeoutErrors", Map.of(
                    "value", metrics.getTimeoutErrorsCount(),
                    "desc", "网络/处理超时错误次数"
            ));
            errors.put("connectionErrors", Map.of(
                    "value", metrics.getConnectionErrorsCount(),
                    "desc", "Redis 连接类错误次数"
            ));
            errors.put("serializationErrors", Map.of(
                    "value", metrics.getSerializationErrorsCount(),
                    "desc", "序列化/反序列化错误次数"
            ));
        } else {
            errors.put("enabled", false);
            errors.put("message", "错误监控已禁用");
        }

        return errors;
    }

    /**
     * 构建健康状态
     *
     * @return 构建后的健康状态
     */
    private Health buildHealthStatus() {
        HealthLevel overall = HealthLevel.UP;
        Health.Builder builder = Health.up();

        // 构建组件健康状态
        Map<String, Object> componentDetails = new HashMap<>();
        for (Map.Entry<String, HealthStatus> entry : healthStatusCache.entrySet()) {
            String component = entry.getKey();
            HealthStatus status = entry.getValue();

            HealthLevel level = computeComponentLevel(component, status);
            // 汇总整体级别（DOWN > DEGRADED > UP）
            if (level == HealthLevel.DOWN) {
                overall = HealthLevel.DOWN;
            } else if (level == HealthLevel.DEGRADED && overall == HealthLevel.UP) {
                overall = HealthLevel.DEGRADED;
            }

            componentDetails.put(component, Map.of(
                    "status", status.isHealthy() ? HealthLevel.UP : HealthLevel.DOWN,
                    "level", level.name(),
                    "message", status.getMessage(),
                    "responseTime", "%dms".formatted(status.getResponseTime())
            ));
        }

        // 添加已注册的 Stream 状态信息
        Map<String, Object> streamStatus = getRegisteredStreamStatus();
        componentDetails.put("registeredStreams", streamStatus);

        // 添加拉取器状态信息
        Map<String, Object> pollerStatus = getPollerStatusDetails();
        componentDetails.put("pollers", pollerStatus);

        // 设置整体状态
        if (overall == HealthLevel.DOWN) {
            builder = Health.down();
        } else if (overall == HealthLevel.DEGRADED) {
            builder = Health.status(HealthLevel.DEGRADED.name());
        } else {
            builder = Health.up();
        }

        return builder
                .withDetail("timestamp", Instant.now().toString())
                .withDetail("checkInterval", "%dms".formatted(properties.getHealthCheck().getInterval().toMillis()))
                .withDetail("components", componentDetails)
                .build();
    }

    /**
     * 获取已注册的 Stream 状态
     *
     * @return Stream 状态 Map
     */
    private Map<String, Object> getRegisteredStreamStatus() {
        Map<String, Object> streamStatus = new HashMap<>();

        try {
            Map<String, Object> pollerSnapshot = reactor.getPollerStatusSnapshot();

            if (pollerSnapshot.isEmpty()) {
                streamStatus.put("count", 0);
                streamStatus.put("streams", new HashMap<>());
                streamStatus.put("message", "当前没有注册的 Stream");
                return streamStatus;
            }

            Map<String, Object> streamDetails = new HashMap<>();
            int totalStreams = 0;
            int healthyStreams = 0;
            int errorStreams = 0;

            for (Map.Entry<String, Object> entry : pollerSnapshot.entrySet()) {
                String pollerKey = entry.getKey();
                Object statusObj = entry.getValue();

                if (statusObj instanceof Map<?, ?> status) {
                    String streamKey = (String) status.get("streamKey");
                    String group = (String) status.get("group");
                    String consumer = (String) status.get("consumer");
                    Boolean active = (Boolean) status.get("active");

                    if (streamKey != null) {
                        totalStreams++;

                        Map<String, Object> streamInfo = new HashMap<>();
                        streamInfo.put("streamKey", streamKey);
                        streamInfo.put("group", group);
                        streamInfo.put("consumer", consumer);
                        streamInfo.put("active", active != null ? active : false);

                        try {
                            // 获取 Stream 详细信息
                            StreamInfo.XInfoStream streamData = redisTemplate.opsForStream().info(streamKey);
                            if (streamData != null) {
                                healthyStreams++;
                                streamInfo.put("status", "healthy");
                                streamInfo.put("length", streamData.streamLength());
                                streamInfo.put("groups", streamData.groupCount());
                                streamInfo.put("lastGeneratedId", streamData.lastGeneratedId());

                                // 获取消费者组信息
                                try {
                                    StreamInfo.XInfoGroups groups = redisTemplate.opsForStream().groups(streamKey);
                                    if (groups != null) {
                                        streamInfo.put("consumerGroups", groups.stream()
                                                .map(g -> Map.of(
                                                        "name", g.groupName(),
                                                        "consumers", g.consumerCount(),
                                                        "pending", g.pendingCount(),
                                                        "lastDeliveredId", g.lastDeliveredId()
                                                ))
                                                .toList());
                                    }
                                } catch (Exception groupEx) {
                                    streamInfo.put("consumerGroupError", groupEx.getMessage());
                                }
                            } else {
                                streamInfo.put("status", "empty");
                                streamInfo.put("message", "Stream 存在但无数据");
                                healthyStreams++; // 空 Stream 也算正常
                            }
                        } catch (Exception streamEx) {
                            errorStreams++;
                            streamInfo.put("status", "error");
                            streamInfo.put("error", streamEx.getMessage());
                        }

                        streamDetails.put(streamKey, streamInfo);
                    }
                }
            }

            streamStatus.put("count", totalStreams);
            streamStatus.put("healthy", healthyStreams);
            streamStatus.put("errors", errorStreams);
            streamStatus.put("streams", streamDetails);

            if (errorStreams > 0) {
                streamStatus.put("message", "%d 个 Stream 正常，%d 个 Stream 异常".formatted(healthyStreams, errorStreams));
            } else if (totalStreams > 0) {
                streamStatus.put("message", "所有 %d 个 Stream 状态正常".formatted(totalStreams));
            } else {
                streamStatus.put("message", "当前没有活跃的 Stream");
            }

        } catch (Exception e) {
            streamStatus.put("count", 0);
            streamStatus.put("error", e.getMessage());
            streamStatus.put("message", "获取 Stream 状态失败");
        }

        return streamStatus;
    }

    /**
     * 获取拉取器状态详情
     *
     * @return 拉取器状态 Map
     */
    private Map<String, Object> getPollerStatusDetails() {
        Map<String, Object> pollerStatus = new HashMap<>();

        try {
            Map<String, Object> pollerSnapshot = reactor.getPollerStatusSnapshot();

            pollerStatus.put("count", pollerSnapshot.size());
            pollerStatus.put("active", pollerSnapshot.values().stream()
                    .mapToInt(p -> {
                        if (p instanceof Map<?, ?> m) {
                            Object active = m.get("active");
                            return (active instanceof Boolean && (Boolean) active) ? 1 : 0;
                        }
                        return 0;
                    })
                    .sum());

            // 构建拉取器详情
            Map<String, Object> pollerDetails = new HashMap<>();
            for (Map.Entry<String, Object> entry : pollerSnapshot.entrySet()) {
                String pollerKey = entry.getKey();
                Object statusObj = entry.getValue();

                if (statusObj instanceof Map<?, ?> status) {
                    Map<String, Object> pollerInfo = new HashMap<>();
                    pollerInfo.put("streamKey", status.get("streamKey"));
                    pollerInfo.put("group", status.get("group"));
                    pollerInfo.put("consumer", status.get("consumer"));
                    pollerInfo.put("active", status.get("active"));
                    pollerInfo.put("startTime", status.get("startTime"));
                    pollerInfo.put("lastActivity", status.get("lastActivity"));
                    pollerInfo.put("messageCount", status.get("messageCount"));
                    pollerInfo.put("errorCount", status.get("errorCount"));

                    pollerDetails.put(pollerKey, pollerInfo);
                }
            }

            pollerStatus.put("details", pollerDetails);

            if (pollerSnapshot.isEmpty()) {
                pollerStatus.put("message", "当前没有活跃的拉取器");
            } else {
                int activeCount = (Integer) pollerStatus.get("active");
                pollerStatus.put("message", "%d 个拉取器活跃，%d 个拉取器总数".formatted(activeCount, pollerSnapshot.size()));
            }

        } catch (Exception e) {
            pollerStatus.put("count", 0);
            pollerStatus.put("active", 0);
            pollerStatus.put("error", e.getMessage());
            pollerStatus.put("message", "获取拉取器状态失败");
        }

        return pollerStatus;
    }

    /**
     * 计算组件健康级别：关键组件失败为 DOWN，非关键失败为 DEGRADED。
     *
     * @param component 组件名称（如 redis、stream）
     * @param status    该组件的健康状态
     * @return 健康级别（UP/DEGRADED/DOWN）
     */
    private HealthLevel computeComponentLevel(String component, HealthStatus status) {
        if (!status.isHealthy()) {
            if ("redis".equals(component) || "stream".equals(component)) {
                return HealthLevel.DOWN;
            }
            return HealthLevel.DEGRADED;
        }
        String msg = status.getMessage() == null ? "" : status.getMessage();
        if (msg.contains("告警") || msg.contains("警告") || msg.contains("降级")) {
            return HealthLevel.DEGRADED;
        }
        return HealthLevel.UP;
    }

    /**
     * 获取特定组件的健康状态
     *
     * @param component 组件名称
     * @return 健康状态
     */
    public HealthStatus getComponentHealth(String component) {
        return healthStatusCache.get(component);
    }

    /**
     * 强制刷新健康检查
     */
    public void refreshHealthCheck() {
        lastCheckTime.set(0);
        performHealthChecks();
    }
}
