package com.richie.component.cache.redis.manage;

import com.richie.context.utils.data.JsonUtils;
import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.function.StreamFunction;
import com.richie.contract.model.BaseStreamMessage;
import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import com.richie.component.cache.redis.bean.MultiStringRedisTemplate;
import com.richie.component.cache.redis.perf.RedisOperationCatalog;
import com.richie.component.cache.redis.perf.RedisPerfGuard;
import com.richie.component.cache.redis.monitor.MetricsErrorRecorder;
import com.richie.component.cache.redis.monitor.RedisStreamMetrics;
import com.richie.component.cache.redis.stream.RedisStreamEventBus;
import com.richie.component.cache.redis.stream.RedisStreamReactor;
import com.richie.component.cache.redis.stream.StreamMessageEvent;
import com.richie.component.cache.redis.tracing.RedisStreamTracingUtils;
import com.richie.component.cache.redis.tracing.TraceableMessageWrapper;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.OpenTelemetry;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Redis Stream 管理器
 *
 * <p>封装 Redis Stream 的可靠消息能力：发布、确认与事件流暴露，并集成指标与链路追踪。
 *
 * <p>主要功能：
 * <ul>
 *   <li>发布：序列化业务载荷与追踪上下文，写入 Redis Stream</li>
 *   <li>确认：对消费完成的记录进行 ACK</li>
 *   <li>事件流：暴露统一的消息事件流供应用订阅</li>
 *   <li>监控：记录发布耗时与失败统计</li>
 *   <li>追踪：发布端创建 PRODUCER Span 并注入上下文</li>
 * </ul>
 *
 * <p>与 Pub/Sub 区别：Stream 持久化、可回溯、支持消费组与 ACK；Pub/Sub 即时广播、无持久化与确认。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-25 17:51:00
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${platform.cache.cache-provider:REDIS}'=='REDIS'")
public class RedisStreamManager implements StreamFunction {

    /** Redis 模板（JSON 序列化） */
    private final @Qualifier("jsonTemplate") MultiRedisTemplate<Object> redisTemplate;

    /** 字符串 Redis 模板 */
    private final MultiStringRedisTemplate stringRedisTemplate;

    /** OpenTelemetry（用于消息追踪） */
    private final OpenTelemetry openTelemetry;

    /** Stream 拉取与发布反应器 */
    private final RedisStreamReactor reactor;

    /** Stream 指标 */
    private final RedisStreamMetrics metrics;

    private final RedisPerfGuard redisPerfGuard;

    /**
     * Redis Stream ID 生成器的 key 前缀
     */
    private static final String STREAM_ID_GENERATOR_KEY_PREFIX = "redis:stream:id:generator:";

    /**
     * 生成唯一的 Redis Stream 记录 ID（基于 Redis 分布式计数器）
     * <p>
     * Redis Stream ID 格式：&lt;毫秒时间戳&gt;-&lt;Redis生成的序列号&gt;
     * <p>
     * 实现原理：
     * <ul>
     *   <li>使用当前毫秒时间戳作为 Redis key 的一部分，确保不同时间段的序列号独立</li>
     *   <li>使用 Redis INCR 命令生成序列号，保证多实例部署下的全局唯一性</li>
     *   <li>为每个时间戳 key 设置过期时间（2秒），避免 key 堆积</li>
     *   <li>拼接消息类型作为后缀，进一步确保不同消息类型的唯一性</li>
     * </ul>
     * <p>
     * 优势：
     * <ul>
     *   <li>多实例部署时保证全局唯一性（Redis 原子操作）</li>
     *   <li>无需引入额外的依赖（如雪花算法 ID 生成器）</li>
     *   <li>自动清理过期 key，避免内存泄漏</li>
     * </ul>
     *
     * @return Redis Stream 记录 ID（格式：毫秒时间戳-Redis序列号-消息类型）
     */
    private String generateUniqueStreamId() {
        long currentTimestamp = System.currentTimeMillis();

        // 构建 Redis key：redis:stream:id:generator:{timestamp}
        String redisKey = STREAM_ID_GENERATOR_KEY_PREFIX + currentTimestamp;

        // 使用 Redis INCR 命令生成序列号（原子操作，保证多实例唯一性）
        // 设置过期时间为 2 秒，确保 key 在时间戳变化后自动清理
        long sequence = GlobalCache.increment(redisKey, 2000L);

        // 如果序列号超过 999999（6 位数字），使用纳秒时间戳的后 6 位作为补充
        // 这样可以处理极端高并发场景（同一毫秒内超过 100 万条消息）
        if (sequence >= 1_000_000) {
            sequence = System.nanoTime() % 1_000_000;
        }

        // 格式：{timestamp}-{sequence}
        return "%d-%d".formatted(currentTimestamp, sequence);
    }

    /**
     * 发布一条 Stream 消息（序列化载荷与追踪上下文后 XADD）。
     *
     * @param streamKey Stream 键
     * @param dto       业务消息
     * @return Redis 记录 ID
     * @apiNote
     * <p><b>时间复杂度</b>：封装为脚本/负载相关（XADD + 序列化 + 分布式 ID），见 {@link RedisOperationCatalog#STREAM_XADD}。
     * <p><b>严禁</b>：toC 同步路径上发布超大 payload 或极高频同 stream 写导致单 key 热点。
     * <p><b>可用</b>：异步任务、可靠投递、与消费组配合的写入。
     * <p><b>注意</b>：含一次 ID 生成（INCR）与一次 XADD；监控序列化大小与耗时。
     */
    @Override
    public <T extends BaseStreamMessage> String publish(String streamKey, T dto) {
        return redisPerfGuard.<String>execute("RedisStreamManager", "publish", RedisOperationCatalog.STREAM_XADD, () -> publishInternal(streamKey, dto));
    }

    private <T extends BaseStreamMessage> String publishInternal(String streamKey, T dto) {
        long startTime = System.currentTimeMillis();

        // 创建发布 Span
        RedisStreamTracingUtils.TracingScope tracingScope = RedisStreamTracingUtils.createPublisherSpan(streamKey, dto.getClass().getSimpleName());

        // 按需采样：发布耗时的带标签计时器
        Timer.Sample sample = null;
        if (metrics.shouldRecordPublishingTimerSample()) {
            sample = Timer.start(metrics.getMeterRegistry());
        }

        try (RedisStreamTracingUtils.TracingScope scope = tracingScope) {
            // 自动包装消息以支持链路追踪（对业务代码透明）
            TraceableMessageWrapper messageWrapper = TraceableMessageWrapper.wrapForPublish(dto, openTelemetry);

            // 构建消息数据 Map
            Map<String, Object> messageData = new HashMap<>();

            // 添加业务负载
            messageData.put("payload", dto);

            // 添加追踪上下文
            messageData.putAll(messageWrapper.traceContext());
            if (messageWrapper.getTraceId() != null) {
                messageData.put("traceId", messageWrapper.getTraceId());
            }
            messageData.put("sampled", messageWrapper.isSampled());

            // 将整个 messageData 序列化为 Base64 编码的字节数组
            byte[] messageBytes = JsonUtils.getInstance().serializeBytes(messageData);
            String messageBase64 = java.util.Base64.getEncoder().encodeToString(messageBytes);

            // 创建 Redis Stream 记录，只包含一个 Base64 编码的消息数据
            // 使用 stringRedisTemplate 而不是 jsonTemplate，确保序列化一致（StringRedisSerializer）
            // 避免 Jackson2JsonRedisSerializer 将字符串序列化为带引号的 JSON 字符串
            Map<String, String> streamData = new HashMap<>();
            streamData.put("data", messageBase64);

            // 显式生成唯一 ID，避免依赖 Redis 自动生成可能出现的重复问题
            // 使用基于 Redis 的分布式 ID 生成器，确保多实例部署下的全局唯一性
            String explicitId = generateUniqueStreamId();
            MapRecord<String, String, String> record = StreamRecords.mapBacked(streamData)
                    .withStreamKey(streamKey)
                    .withId(RecordId.of(explicitId));
            String result = Objects.requireNonNull(stringRedisTemplate.opsForStream().add(record)).getValue();

            // 记录监控指标
            metrics.recordMessagePublished(streamKey);
            metrics.recordPublishingDuration(streamKey, Duration.ofMillis(System.currentTimeMillis() - startTime));

            // 添加 Span 属性
            if (scope != null && scope.getSpan() != null) {
                scope.getSpan().setAttribute("message.recordId", result);
                scope.getSpan().setAttribute("publishing.duration", System.currentTimeMillis() - startTime);
                scope.getSpan().setAttribute("message.traceId", messageWrapper.getTraceId());
                scope.getSpan().setAttribute("message.spanId", messageWrapper.getSpanId());
                scope.getSpan().setAttribute("message.sampled", messageWrapper.isSampled());
            }

            log.debug("消息发布成功: streamKey={}, recordId={}, traceId={}",
                    streamKey, result, messageWrapper.getTraceId());
            return result;

        } catch (Exception e) {
            // 记录错误指标
            metrics.recordMessageFailed(streamKey, "unknown", "publishing_error");
            MetricsErrorRecorder.recordTimeoutIfAny(metrics, streamKey, e);
            MetricsErrorRecorder.recordConnectionIfAny(metrics, streamKey, e);
            MetricsErrorRecorder.recordSerializationIfAny(metrics, streamKey, e);

            // 记录错误到 Span
            if (tracingScope != null && tracingScope.getSpan() != null) {
                RedisStreamTracingUtils.recordError(tracingScope.getSpan(), e);
            }

            log.error("消息发布失败: streamKey={}", streamKey, e);
            throw e;
        } finally {
            if (sample != null) {
                sample.stop(metrics.getPublishingDurationTimer(streamKey));
            }
        }
    }

    @Override
    public Flux<StreamMessageEvent<?>> messageFlow() {
        return RedisStreamEventBus.messageFlow;
    }


    /**
     * 消费组确认一条消息（XACK）。
     *
     * @param streamKey Stream 键
     * @param group     消费组名
     * @param recordId  记录 ID
     * @apiNote
     * <p><b>时间复杂度</b>：{@code O(1)}（XACK）。
     * <p><b>严禁</b>：在 toC 请求线程内批量 ACK 大量 pending（应异步或流水线）。
     * <p><b>可用</b>：消费者处理完成后确认。
     * <p><b>注意</b>：ACK 失败需结合 pending 监控与重试策略。
     */
    @Override
    public void acknowledge(String streamKey, String group, String recordId) {
        redisPerfGuard.execute("RedisStreamManager", "acknowledge", RedisOperationCatalog.STREAM_ACK, () -> {
            try {
                redisTemplate.opsForStream().acknowledge(streamKey, group, recordId);

                // 记录监控指标
                metrics.recordMessageAcknowledged(streamKey, group);

                log.debug("消息确认成功: streamKey={}, group={}, recordId={}", streamKey, group, recordId);

            } catch (Exception e) {
                // 记录错误指标
                metrics.recordMessageFailed(streamKey, group, "acknowledge_error");
                log.error("消息确认失败: streamKey={}, group={}, recordId={}", streamKey, group, recordId, e);
                throw e;
            }
        });
    }

    /**
     * 优雅停机
     */
    @PreDestroy
    public void shutdown() {
        try {
            log.info("开始优雅停机 Redis Stream Manager");

            reactor.shutdown();
            log.info("Redis Stream Manager 优雅停机完成");
        } catch (Exception e) {
            log.error("Redis Stream Manager 关闭异常", e);
        }
    }


}
