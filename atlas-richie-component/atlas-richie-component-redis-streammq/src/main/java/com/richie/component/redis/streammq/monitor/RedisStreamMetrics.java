/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.redis.streammq.monitor;

import com.richie.component.redis.streammq.config.monitor.RedisStreamMonitoringProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis Stream 监控指标收集器
 *
 * <p>基于 Micrometer 提供完整的 Redis Stream 监控指标，包括：
 * <ul>
 *   <li><strong>业务指标</strong>：消息发布、消费、确认、失败计数</li>
 *   <li><strong>性能指标</strong>：处理耗时、吞吐量、延迟统计</li>
 *   <li><strong>系统指标</strong>：活跃消费者、连接数、内存使用</li>
 *   <li><strong>错误指标</strong>：错误分类、重试统计、超时计数</li>
 * </ul>
 *
 * <p><strong>使用示例：</strong>
 * <pre>{@code
 * &#64;Autowired
 * private RedisStreamMetrics metrics;
 *
 * // 记录消息发布
 * metrics.recordMessagePublished("order-events");
 *
 * // 记录消息处理耗时
 * Timer.Sample sample = Timer.start(metrics.getMeterRegistry());
 * try {
 *     // 处理消息
 * } finally {
 *     sample.stop(metrics.getProcessingDurationTimer("order-events"));
 * }
 * }</pre>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-09-15
 */
@Slf4j
@Component
@ConditionalOnClass(MeterRegistry.class)
public class RedisStreamMetrics {

    /**
     *  获取 MeterRegistry 实例
     */
    @Getter
    private final MeterRegistry meterRegistry;

    // ==================== 业务指标 ====================

    /** 消息发布计数器 */
    private final Counter messagesPublished;

    /** 消息消费计数器 */
    private final Counter messagesConsumed;

    /** 消息确认计数器 */
    private final Counter messagesAcknowledged;

    /** 消息处理失败计数器 */
    private final Counter messagesFailed;

    /** 消息重试计数器 */
    private final Counter messagesRetried;

    // ==================== 性能指标 ====================

    /** 消息处理耗时计时器 */
    private final Timer processingDuration;

    /** 拉取操作耗时计时器 */
    private final Timer pollingDuration;

    /** 消息发布耗时计时器 */
    private final Timer publishingDuration;

    // ==================== 系统指标 ====================

    /** 活跃消费者数量 */
    private final AtomicInteger activeConsumers = new AtomicInteger(0);

    /** 活跃拉取器数量 */
    private final AtomicInteger activePollers = new AtomicInteger(0);

    /** 活跃连接数 */
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    /** 消息积压数量 */
    private final AtomicLong messageBacklog = new AtomicLong(0);

    // ==================== 错误指标 ====================

    /** 总错误数 */
    private final Counter totalErrors;

    /** 超时错误数 */
    private final Counter timeoutErrors;

    /** 连接错误数 */
    private final Counter connectionErrors;

    /** 序列化错误数 */
    private final Counter serializationErrors;

    // ==================== 配置与构造函数 ====================

    /** 监控配置 */
    private final RedisStreamMonitoringProperties properties;

    /**
     * 构造 Stream 指标收集器。
     *
     * @param meterRegistry 指标注册表
     * @param properties    监控配置
     */
    public RedisStreamMetrics(MeterRegistry meterRegistry, RedisStreamMonitoringProperties properties) {
        this.meterRegistry = meterRegistry;
        this.properties = properties;

        // 初始化业务指标
        this.messagesPublished = Counter.builder("redis.stream.messages.published")
                .description("Redis Stream 消息发布总数")
                .register(meterRegistry);

        this.messagesConsumed = Counter.builder("redis.stream.messages.consumed")
                .description("Redis Stream 消息消费总数")
                .register(meterRegistry);

        this.messagesAcknowledged = Counter.builder("redis.stream.messages.acknowledged")
                .description("Redis Stream 消息确认总数")
                .register(meterRegistry);

        this.messagesFailed = Counter.builder("redis.stream.messages.failed")
                .description("Redis Stream 消息处理失败总数")
                .register(meterRegistry);

        this.messagesRetried = Counter.builder("redis.stream.messages.retried")
                .description("Redis Stream 消息重试总数")
                .register(meterRegistry);

        // 初始化性能指标
        this.processingDuration = Timer.builder("redis.stream.processing.duration.global")
                .description("Redis Stream 消息处理耗时（全局）")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram(properties.getMetrics().isDetailed())
                .register(meterRegistry);

        this.pollingDuration = Timer.builder("redis.stream.polling.duration.global")
                .description("Redis Stream 拉取操作耗时（全局）")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.publishingDuration = Timer.builder("redis.stream.publishing.duration.global")
                .description("Redis Stream 消息发布耗时（全局）")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        // 初始化错误指标
        this.totalErrors = Counter.builder("redis.stream.errors.total")
                .description("Redis Stream 总错误数")
                .register(meterRegistry);

        this.timeoutErrors = Counter.builder("redis.stream.errors.timeout")
                .description("Redis Stream 超时错误数")
                .register(meterRegistry);

        this.connectionErrors = Counter.builder("redis.stream.errors.connection")
                .description("Redis Stream 连接错误数")
                .register(meterRegistry);

        this.serializationErrors = Counter.builder("redis.stream.errors.serialization")
                .description("Redis Stream 序列化错误数")
                .register(meterRegistry);

        // 注册 Gauge 指标
        registerGaugeMetrics();

        log.info("Redis Stream 监控指标收集器初始化完成: detailed={}, samplingRate={}",
                properties.getMetrics().isDetailed(), properties.getMetrics().getSamplingRate());
    }

    // ==================== 业务指标方法 ====================

    /**
     * 记录消息发布
     *
     * @param streamKey Stream 键名
     */
    public void recordMessagePublished(String streamKey) {
        if (!properties.getBusinessMonitoring().isEnabled() || shouldSkipSampling()) return;
        messagesPublished.increment();
        log.debug("记录消息发布: streamKey={}", streamKey);
    }

    /**
     * 记录消息消费
     *
     * @param streamKey Stream 键名
     * @param consumerGroup 消费者组
     */
    public void recordMessageConsumed(String streamKey, String consumerGroup) {
        if (!properties.getBusinessMonitoring().isEnabled() || shouldSkipSampling()) return;
        messagesConsumed.increment();
        log.debug("记录消息消费: streamKey={}, group={}", streamKey, consumerGroup);
    }

    /**
     * 记录消息确认
     *
     * @param streamKey Stream 键名
     * @param consumerGroup 消费者组
     */
    public void recordMessageAcknowledged(String streamKey, String consumerGroup) {
        if (!properties.getBusinessMonitoring().isEnabled() || shouldSkipSampling()) return;
        messagesAcknowledged.increment();
        log.debug("记录消息确认: streamKey={}, group={}", streamKey, consumerGroup);
    }

    /**
     * 记录消息处理失败
     *
     * @param streamKey Stream 键名
     * @param consumerGroup 消费者组
     * @param errorType 错误类型
     */
    public void recordMessageFailed(String streamKey, String consumerGroup, String errorType) {
        if (!properties.getErrorMonitoring().isEnabled() || shouldSkipSampling()) return;
        messagesFailed.increment();
        totalErrors.increment();
        log.debug("记录消息处理失败: streamKey={}, group={}, errorType={}", streamKey, consumerGroup, errorType);
    }

    /**
     * 记录消息重试
     *
     * @param streamKey Stream 键名
     * @param consumerGroup 消费者组
     */
    public void recordMessageRetried(String streamKey, String consumerGroup) {
        if (!properties.getBusinessMonitoring().isEnabled() || shouldSkipSampling()) return;
        messagesRetried.increment();
        log.debug("记录消息重试: streamKey={}, group={}", streamKey, consumerGroup);
    }

    // ==================== 性能指标方法 ====================

    /**
     * 记录消息处理耗时
     *
     * @param streamKey Stream 键名
     * @param duration 处理耗时
     */
    public void recordProcessingDuration(String streamKey, Duration duration) {
        if (!properties.getPerformance().isEnabled() || !properties.getPerformance().isRecordProcessingTime() || shouldSkipSampling()) return;
        processingDuration.record(duration);
        log.debug("记录处理耗时: streamKey={}, duration={}ms", streamKey, duration.toMillis());
    }

    /**
     * 记录拉取操作耗时
     *
     * @param streamKey Stream 键名
     * @param duration 拉取耗时
     */
    public void recordPollingDuration(String streamKey, Duration duration) {
        if (!properties.getPerformance().isEnabled() || !properties.getPerformance().isRecordPollingTime() || shouldSkipSampling()) return;
        pollingDuration.record(duration);
        log.debug("记录拉取耗时: streamKey={}, duration={}ms", streamKey, duration.toMillis());
    }

    /**
     * 记录消息发布耗时
     *
     * @param streamKey Stream 键名
     * @param duration 发布耗时
     */
    public void recordPublishingDuration(String streamKey, Duration duration) {
        if (!properties.getPerformance().isEnabled() || !properties.getPerformance().isRecordPublishingTime() || shouldSkipSampling()) return;
        publishingDuration.record(duration);
        log.debug("记录发布耗时: streamKey={}, duration={}ms", streamKey, duration.toMillis());
    }

    // ==================== 系统指标方法 ====================

    /**
     * 增加活跃消费者数量
     */
    public void incrementActiveConsumers() {
        int count = activeConsumers.incrementAndGet();
        log.debug("活跃消费者数量增加: {}", count);
    }

    /**
     * 减少活跃消费者数量
     */
    public void decrementActiveConsumers() {
        int count = activeConsumers.decrementAndGet();
        log.debug("活跃消费者数量减少: {}", count);
    }

    /**
     * 增加活跃拉取器数量
     */
    public void incrementActivePollers() {
        int count = activePollers.incrementAndGet();
        log.debug("活跃拉取器数量增加: {}", count);
    }

    /**
     * 减少活跃拉取器数量
     */
    public void decrementActivePollers() {
        int count = activePollers.decrementAndGet();
        log.debug("活跃拉取器数量减少: {}", count);
    }

    /**
     * 设置消息积压数量
     *
     * @param backlog 积压数量
     */
    public void setMessageBacklog(long backlog) {
        messageBacklog.set(backlog);
        log.debug("设置消息积压数量: {}", backlog);
    }

    // ==================== 错误指标方法 ====================

    /**
     * 记录超时错误
     *
     * @param streamKey Stream 键名
     */
    public void recordTimeoutError(String streamKey) {
        if (!properties.getErrorMonitoring().isEnabled() || shouldSkipSampling()) return;
        timeoutErrors.increment();
        totalErrors.increment();
        log.debug("记录超时错误: streamKey={}", streamKey);
    }

    /**
     * 记录连接错误
     *
     * @param streamKey Stream 键名
     */
    public void recordConnectionError(String streamKey) {
        if (!properties.getErrorMonitoring().isEnabled() || shouldSkipSampling()) return;
        connectionErrors.increment();
        totalErrors.increment();
        log.debug("记录连接错误: streamKey={}", streamKey);
    }

    /**
     * 记录序列化错误
     *
     * @param streamKey Stream 键名
     */
    public void recordSerializationError(String streamKey) {
        if (!properties.getErrorMonitoring().isEnabled() || shouldSkipSampling()) return;
        serializationErrors.increment();
        totalErrors.increment();
        log.debug("记录序列化错误: streamKey={}", streamKey);
    }

    // ==================== 工具方法 ====================

    /**
     * 获取消息处理耗时计时器
     *
     * @param streamKey Stream 键名
     * @return Timer 实例
     */
    public Timer getProcessingDurationTimer(String streamKey) {
        return Timer.builder("redis.stream.processing.duration")
                .description("Redis Stream 消息处理耗时")
                .tag("stream", streamKey)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    /**
     * 获取拉取操作耗时计时器
     *
     * @param streamKey Stream 键名
     * @return Timer 实例
     */
    public Timer getPollingDurationTimer(String streamKey) {
        return Timer.builder("redis.stream.polling.duration")
                .description("Redis Stream 拉取操作耗时")
                .tag("stream", streamKey)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    /**
     * 获取消息发布耗时计时器
     *
     * @param streamKey Stream 键名
     * @return Timer 实例
     */
    public Timer getPublishingDurationTimer(String streamKey) {
        return Timer.builder("redis.stream.publishing.duration")
                .description("Redis Stream 消息发布耗时")
                .tag("stream", streamKey)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    // ==================== 私有方法 ====================

    /**
     * 注册 Gauge 指标
     */
    private void registerGaugeMetrics() {
        // 活跃消费者数量
        Gauge.builder("redis.stream.consumers.active", activeConsumers, AtomicInteger::get)
                .description("Redis Stream 活跃消费者数量")
                .register(meterRegistry);

        // 活跃拉取器数量
        Gauge.builder("redis.stream.pollers.active", activePollers, AtomicInteger::get)
                .description("Redis Stream 活跃拉取器数量")
                .register(meterRegistry);

        // 消息积压数量
        Gauge.builder("redis.stream.backlog", messageBacklog, AtomicLong::get)
                .description("Redis Stream 消息积压数量")
                .register(meterRegistry);

        // 活跃连接数
        Gauge.builder("redis.stream.connections.active", activeConnections, AtomicInteger::get)
                .description("Redis Stream 活跃连接数")
                .register(meterRegistry);
    }

    /**
     * 检查是否应该跳过采样
     *
     * @return true 如果应该跳过采样，false 如果应该进行采样
     */
    private boolean shouldSkipSampling() {
        double rate = properties.getMetrics().getSamplingRate();
        if (rate >= 1.0) return false;  // 100% 采样，不跳过
        if (rate <= 0.0) return true;   // 0% 采样，跳过所有
        return ThreadLocalRandom.current().nextDouble() >= rate;  // 根据概率决定是否跳过
    }

    /**
     * 是否记录处理耗时的带标签计时器样本
     *
     * @return true 表示应记录样本
     */
    public boolean shouldRecordProcessingTimerSample() {
        if (!properties.getPerformance().isEnabled()) {
            return false;
        }
        if (!properties.getPerformance().isRecordProcessingTime()) {
            return false;
        }
        return !shouldSkipSampling();
    }

    /**
     * 是否记录拉取耗时的带标签计时器样本
     *
     * @return true 表示应记录样本
     */
    public boolean shouldRecordPollingTimerSample() {
        if (!properties.getPerformance().isEnabled()) {
            return false;
        }
        if (!properties.getPerformance().isRecordPollingTime()) {
            return false;
        }
        return !shouldSkipSampling();
    }

    /**
     * 是否记录发布耗时的带标签计时器样本
     *
     * @return true 表示应记录样本
     */
    public boolean shouldRecordPublishingTimerSample() {
        if (!properties.getPerformance().isEnabled()) {
            return false;
        }
        if (!properties.getPerformance().isRecordPublishingTime()) {
            return false;
        }
        return !shouldSkipSampling();
    }

    // ==================== 指标值获取方法 ====================

    /**
     * 获取消息发布计数
     *
     * @return 发布消息总数
     */
    public double getMessagesPublishedCount() {
        return messagesPublished != null ? messagesPublished.count() : 0.0;
    }

    /**
     * 获取消息消费计数
     *
     * @return 消费消息总数
     */
    public double getMessagesConsumedCount() {
        return messagesConsumed != null ? messagesConsumed.count() : 0.0;
    }

    /**
     * 获取消息确认计数
     *
     * @return 已确认消息总数
     */
    public double getMessagesAcknowledgedCount() {
        return messagesAcknowledged != null ? messagesAcknowledged.count() : 0.0;
    }

    /**
     * 获取消息失败计数
     *
     * @return 处理失败消息总数
     */
    public double getMessagesFailedCount() {
        return messagesFailed != null ? messagesFailed.count() : 0.0;
    }

    /**
     * 获取消息重试计数
     *
     * @return 重试次数
     */
    public double getMessagesRetriedCount() {
        return messagesRetried != null ? messagesRetried.count() : 0.0;
    }

    /**
     * 获取总错误计数
     *
     * @return 总错误数
     */
    public double getTotalErrorsCount() {
        return totalErrors != null ? totalErrors.count() : 0.0;
    }

    /**
     * 获取超时错误计数
     *
     * @return 超时错误数
     */
    public double getTimeoutErrorsCount() {
        return timeoutErrors != null ? timeoutErrors.count() : 0.0;
    }

    /**
     * 获取连接错误计数
     *
     * @return 连接错误数
     */
    public double getConnectionErrorsCount() {
        return connectionErrors != null ? connectionErrors.count() : 0.0;
    }

    /**
     * 获取序列化错误计数
     *
     * @return 序列化错误数
     */
    public double getSerializationErrorsCount() {
        return serializationErrors != null ? serializationErrors.count() : 0.0;
    }

    /**
     * 获取活跃消费者计数
     *
     * @return 活跃消费者数
     */
    public double getActiveConsumersCount() {
        return activeConsumers.get();
    }

    /**
     * 获取活跃拉取器计数
     *
     * @return 活跃拉取器数
     */
    public double getActivePollersCount() {
        return activePollers.get();
    }

    /**
     * 获取消息积压计数
     *
     * @return 积压消息数
     */
    public double getMessageBacklogCount() {
        return messageBacklog.get();
    }

    /**
     * 获取活跃连接计数
     *
     * @return 活跃连接数
     */
    public double getActiveConnectionsCount() {
        return activeConnections.get();
    }

    /**
     * 获取处理耗时统计
     *
     * @return 耗时统计 Map
     */
    public Map<String, Object> getProcessingDurationStats() {
        return getDurationStats(processingDuration);
    }

    /**
     * 获取拉取耗时统计
     *
     * @return 耗时统计 Map
     */
    public Map<String, Object> getPollingDurationStats() {
        return getDurationStats(pollingDuration);
    }

    /**
     * 获取发布耗时统计
     *
     * @return 耗时统计 Map
     */
    public Map<String, Object> getPublishingDurationStats() {
        return getDurationStats(publishingDuration);
    }

    @Nonnull
    private Map<String, Object> getDurationStats(Timer timer) {
        if (timer != null) {
            return Map.of(
                    "count", Map.of(
                            "value", timer.count(),
                            "desc", "样本数量"
                    ),
                    "totalTime", Map.of(
                            "value", timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS),
                            "desc", "总耗时(毫秒)"
                    ),
                    "mean", Map.of(
                            "value", timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS),
                            "desc", "平均耗时(毫秒)"
                    ),
                    "max", Map.of(
                            "value", timer.max(java.util.concurrent.TimeUnit.MILLISECONDS),
                            "desc", "最大耗时(毫秒)"
                    )
            );
        }
        return Map.of(
                "count", Map.of("value", 0, "desc", "样本数量"),
                "totalTime", Map.of("value", 0, "desc", "总耗时(毫秒)"),
                "mean", Map.of("value", 0, "desc", "平均耗时(毫秒)"),
                "max", Map.of("value", 0, "desc", "最大耗时(毫秒)")
        );
    }

}
