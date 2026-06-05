package com.richie.component.redis.streammq.stream;

import com.richie.contract.model.BaseStreamMessage;
import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import com.richie.component.cache.redis.bean.MultiStringRedisTemplate;
import org.springframework.data.redis.connection.stream.RecordId;
import com.richie.component.redis.streammq.bean.StreamMessage;
import com.richie.component.redis.streammq.monitor.MetricsErrorRecorder;
import com.richie.component.redis.streammq.monitor.RedisStreamMetrics;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis Stream 拉取器
 *
 * <p>负责从 Redis Stream 拉取消息，并发布到事件总线供消费。
 *
 * <p>主要功能：
 * <ul>
 *   <li>启动/停止指定 Stream-Group-Consumer 的长轮询拉取器</li>
 *   <li>将拉取到的消息发布为事件，供业务消费者订阅处理</li>
 *   <li>维护拉取器的活动状态与统计快照，支持健康检查与管理端点查询</li>
 *   <li>记录拉取/发布过程的性能与错误指标</li>
 * </ul>
 *
 * <p>设计说明：
 * <ul>
 *   <li>通过调度线程池执行长轮询，按配置的 blockMs 进行周期性拉取</li>
 *   <li>通过 Reactor 控制总线响应状态查询，避免与 Web 层强耦合</li>
 *   <li>按需采样 Micrometer Timer，降低指标采集开销</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-09-16
 */
@Slf4j
@Component
public class RedisStreamReactor {

    /** Redis 模板（JSON 序列化） */
    @Qualifier("jsonTemplate")
    private final MultiRedisTemplate<Object> redisTemplate;

    /** 字符串 Redis 模板 */
    private final MultiStringRedisTemplate stringRedisTemplate;

    /** Stream 指标 */
    private final RedisStreamMetrics metrics;

    /** 定时调度器 */
    private final ScheduledExecutorService scheduler;

    /** 活跃拉取器任务（streamKey -> ScheduledFuture，用于取消） */
    private final Map<String, ScheduledFuture<?>> activePollers = new ConcurrentHashMap<>();

    /** 拉取器配置（streamKey -> 配置） */
    private final Map<String, PollerConfig> pollerConfigs = new ConcurrentHashMap<>();

    /** 拉取器状态快照（供健康检查与端点查询） */
    private final Map<String, PollerStatus> pollerStatusMap = new ConcurrentHashMap<>();

    /** 总拉取次数 */
    private final AtomicLong totalPollCount = new AtomicLong(0);

    /** 总消息数 */
    private final AtomicLong totalMessageCount = new AtomicLong(0);

    /** 总错误数 */
    private final AtomicLong totalErrorCount = new AtomicLong(0);

    /** 控制总线订阅（用于拉取器状态查询/响应） */
    private final Disposable controlSubscription;

    /** 是否正在优雅停机 */
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    /**
     * 构造函数
     *
     * @param redisTemplate Redis 模板
     * @param stringRedisTemplate 字符串 Redis 模板
     * @param metrics Stream 指标
     */
    public RedisStreamReactor(@Qualifier("jsonTemplate") MultiRedisTemplate<Object> redisTemplate, MultiStringRedisTemplate stringRedisTemplate, RedisStreamMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.metrics = metrics;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "redis-stream-reactor-%d".formatted(System.currentTimeMillis()));
            t.setDaemon(true);
            return t;
        });
        // 订阅控制总线：响应查询类事件
        this.controlSubscription = RedisStreamControlBus.controlFlow()
                .publishOn(Schedulers.boundedElastic())
                .subscribe(evt -> {
                    if (evt instanceof RedisStreamControlBus.PollerStatusQuery(String correlationId)) {
                        RedisStreamControlBus.publish(new RedisStreamControlBus.PollerStatusResponse(
                                correlationId, getPollerStatusSnapshot()
                        ));
                    }
                });
    }

    /**
     * 启动自适应轮询拉取器
     *
     * <p>自适应轮询策略：
     * <ul>
     *   <li>有消息时：立即进行下次拉取（或很短延迟，如 50ms），实现高吞吐量</li>
     *   <li>无消息时：等待 blockMs（长轮询时间，如 2000ms），减少无效轮询</li>
     * </ul>
     *
     * <p>相比固定间隔轮询，自适应轮询可以：
     * <ul>
     *   <li>显著降低延迟：有消息时延迟从 2 秒降低到 &lt; 100ms</li>
     *   <li>提高吞吐量：消息密集时可以连续拉取，无需等待固定间隔</li>
     *   <li>节省资源：无消息时保持长轮询，避免频繁空轮询</li>
     * </ul>
     *
     * @param <T>      消息负载类型
     * @param streamKey Stream 键名
     * @param group     消费者组名
     * @param consumer  消费者名称
     * @param count     单次拉取数量
     * @param blockMs   阻塞等待时间（毫秒）
     * @param type      消息类型 Class
     */
    public <T extends BaseStreamMessage> void startPolling(String streamKey, String group, String consumer,
                                                           int count, long blockMs, Class<T> type) {
        String pollerKey = "%s:%s:%s".formatted(streamKey, group, consumer);

        // 检查是否已经存在拉取器
        if (activePollers.containsKey(pollerKey)) {
            log.debug("拉取器已存在，跳过启动: streamKey={}, group={}, consumer={}", streamKey, group, consumer);
            return;
        }

        // 确保 Stream 与消费者组存在
        try {
            ensureStreamAndGroup(streamKey, group);
        } catch (Exception e) {
            log.warn("初始化 Stream/Group 失败，将在运行时重试: stream={}, group={}, err={}", streamKey, group, e.getMessage());
        }

        try {
            // 保存拉取器配置
            PollerConfig config = new PollerConfig(streamKey, group, consumer, count, blockMs);
            pollerConfigs.put(pollerKey, config);

            // 初始化拉取器状态
            pollerStatusMap.put(pollerKey, new PollerStatus(streamKey, group, consumer, true,
                    Instant.now(), Instant.now(), new AtomicLong(0), new AtomicLong(0)));

            // 立即启动第一次拉取
            scheduleNextPoll(pollerKey, 0);

            metrics.incrementActivePollers();

            log.info("自适应轮询拉取器启动成功: stream={}, group={}, consumer={}, count={}, blockMs={}, " +
                    "有消息时立即拉取，无消息时等待{}ms",
                    streamKey, group, consumer, count, blockMs, blockMs);

        } catch (Exception e) {
            // 记录错误指标
            metrics.recordMessageFailed(streamKey, group, "poller_start_error");
            log.error("拉取器启动失败: stream={}, group={}, consumer={}", streamKey, group, consumer, e);
            throw e;
        }
    }

    /**
     * 调度下一次拉取
     *
     * @param pollerKey 拉取器键
     * @param delayMs 延迟时间（毫秒），0 表示立即执行
     */
    private void scheduleNextPoll(String pollerKey, long delayMs) {
        PollerConfig config = pollerConfigs.get(pollerKey);
        if (config == null) {
            log.warn("拉取器配置不存在，停止调度: pollerKey={}", pollerKey);
            return;
        }

        // 检查是否正在停机
        if (isShuttingDown.get()) {
            log.debug("检测到停机标志，停止调度下次拉取: pollerKey={}", pollerKey);
            return;
        }

        // 取消之前的任务（如果存在）
        ScheduledFuture<?> oldFuture = activePollers.remove(pollerKey);
        if (oldFuture != null && !oldFuture.isDone()) {
            oldFuture.cancel(false);
        }

        // 调度下一次拉取
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            performPoll(pollerKey);
        }, delayMs, TimeUnit.MILLISECONDS);

        // 记录新的调度任务
        activePollers.put(pollerKey, future);
    }

    /**
     * 执行一次拉取操作，并根据结果决定下次拉取时间
     *
     * @param pollerKey 拉取器键
     */
    private void performPoll(String pollerKey) {
        PollerConfig config = pollerConfigs.get(pollerKey);
        if (config == null) {
            log.warn("拉取器配置不存在，停止拉取: pollerKey={}", pollerKey);
            return;
        }

        // 检查是否正在停机
        if (isShuttingDown.get()) {
            log.debug("检测到停机标志，停止拉取器: pollerKey={}", pollerKey);
            return;
        }

        long startTime = System.currentTimeMillis();
        Timer.Sample sample = null;
        if (metrics.shouldRecordPollingTimerSample()) {
            sample = Timer.start(metrics.getMeterRegistry());
        }

        try {
            // 更新拉取器活动时间
            markPollerActivity(pollerKey);

            // 执行拉取，返回是否拉取到消息
            boolean hasMessages = pollOnceWithResult(config.streamKey, config.group, config.consumer, config.count);

            // 记录性能指标
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordPollingDuration(config.streamKey, Duration.ofMillis(duration));
            totalPollCount.incrementAndGet();

            // 根据是否拉取到消息决定下次拉取时间
            if (hasMessages) {
                // 有消息：立即进行下次拉取（或很短延迟，如 50ms），实现高吞吐量
                long immediateDelay = 50L; // 50ms 延迟，避免过于频繁的轮询
                scheduleNextPoll(pollerKey, immediateDelay);

                log.debug("拉取到消息，{}ms后立即进行下次拉取: stream={}, group={}, consumer={}, duration={}ms",
                        immediateDelay, config.streamKey, config.group, config.consumer, duration);
            } else {
                // 无消息：等待 blockMs（长轮询时间），减少无效轮询
                scheduleNextPoll(pollerKey, config.blockMs);

                log.debug("未拉取到消息，{}ms后进行下次长轮询: stream={}, group={}, consumer={}, duration={}ms",
                        config.blockMs, config.streamKey, config.group, config.consumer, duration);
            }

        } catch (Exception e) {
            // 检查是否是连接关闭错误，如果是则直接退出
            if (isConnectionClosedError(e)) {
                log.info("检测到Redis连接已关闭，停止拉取器: pollerKey={}", pollerKey);
                activePollers.remove(pollerKey);
                return;
            }

            // 记录错误指标
            totalErrorCount.incrementAndGet();
            metrics.recordMessageFailed(config.streamKey, config.group, "polling_error");
            incrementPollerErrorCount(pollerKey);
            // 细化错误分类
            MetricsErrorRecorder.recordTimeoutIfAny(metrics, config.streamKey, e);
            MetricsErrorRecorder.recordConnectionIfAny(metrics, config.streamKey, e);
            MetricsErrorRecorder.recordSerializationIfAny(metrics, config.streamKey, e);

            // 如果是 NOGROUP 错误则尝试创建组
            if (isNoGroupError(e)) {
                try {
                    log.warn("检测到 NOGROUP 错误，尝试创建消费者组: stream={}, group={}", config.streamKey, config.group);
                    ensureStreamAndGroup(config.streamKey, config.group);
                } catch (Exception createEx) {
                    log.error("创建消费者组失败: stream={}, group={}", config.streamKey, config.group, createEx);
                }
            }

            log.error("拉取Stream消息异常: pollerKey={}, stream={}, group={}, consumer={}",
                    pollerKey, config.streamKey, config.group, config.consumer, e);

            // 发生错误时，等待一段时间后重试（避免错误时过于频繁重试）
            scheduleNextPoll(pollerKey, Math.min(config.blockMs, 1000L)); // 最多等待 1 秒

        } finally {
            if (sample != null) {
                sample.stop(metrics.getPollingDurationTimer(config.streamKey));
            }
        }
    }

    /**
     * 停止指定的拉取器
     *
     * @param streamKey Stream 键名
     * @param group     消费者组名
     * @param consumer  消费者名称
     */
    public void stopPolling(String streamKey, String group, String consumer) {
        String pollerKey = "%s:%s:%s".formatted(streamKey, group, consumer);
        ScheduledFuture<?> future = activePollers.remove(pollerKey);

        if (future != null) {
            future.cancel(false); // 使用 false 避免中断正在执行的任务
            metrics.decrementActivePollers();
            log.info("拉取器停止成功: stream={}, group={}, consumer={}", streamKey, group, consumer);
            var status = pollerStatusMap.get(pollerKey);
            if (status != null) {
                status.active = false;
                status.lastActivity = Instant.now();
            }
        } else {
            log.debug("拉取器不存在，无需停止: stream={}, group={}, consumer={}", streamKey, group, consumer);
        }

        // 清理配置
        pollerConfigs.remove(pollerKey);
    }

    /**
     * 停止所有拉取器
     */
    public void stopAllPollers() {
        log.info("开始停止所有拉取器，当前活跃拉取器数量: {}", activePollers.size());

        for (Map.Entry<String, ScheduledFuture<?>> entry : activePollers.entrySet()) {
            String pollerKey = entry.getKey();
            ScheduledFuture<?> future = entry.getValue();

            // 立即取消定时任务，不等待完成
            boolean cancelled = future.cancel(false); // 使用 false 避免中断正在执行的任务
            if (cancelled) {
                log.debug("拉取器已取消: {}", pollerKey);
            } else {
                log.warn("拉取器取消失败: {}", pollerKey);
            }

            metrics.decrementActivePollers();
        }
        activePollers.clear();
        pollerConfigs.clear();
        log.info("所有拉取器已停止");
    }

    /**
     * 执行一次拉取操作（保留原有方法以兼容其他调用）
     *
     * @param streamKey Stream 键名
     * @param group     消费者组名
     * @param consumer  消费者名称
     * @param count     单次拉取数量
     */
    public void pollOnce(String streamKey, String group, String consumer, int count) {
        pollOnceWithResult(streamKey, group, consumer, count);
    }

    /**
     * 执行一次拉取操作，并返回是否拉取到消息
     *
     * @param streamKey Stream 键
     * @param group 消费者组
     * @param consumer 消费者名称
     * @param count 拉取数量
     * @return true 表示拉取到消息，false 表示未拉取到消息
     */
    private boolean pollOnceWithResult(String streamKey, String group, String consumer, int count) {
        // 检查是否正在停机，如果是则跳过拉取
        if (isShuttingDown.get()) {
            log.debug("服务正在停机，跳过Stream消息拉取: stream={}, group={}, consumer={}",
                    streamKey, group, consumer);
            return false;
        }

        List<ObjectRecord<String, String>> records;
        try {
            // 使用 StringRedisTemplate 读取 Stream，因为 Stream 中的 "data" 字段是 Base64 编码的字符串
            // 使用 StringRedisSerializer 而不是 Jackson2JsonRedisSerializer，避免 JSON 反序列化错误
            @SuppressWarnings("unchecked") var tmp = stringRedisTemplate.opsForStream().read(
                    String.class,
                    Consumer.from(group, consumer),
                    StreamReadOptions.empty().count(count).block(Duration.ofMillis(2000)),
                    StreamOffset.create(streamKey, ReadOffset.lastConsumed())
            );
            records = tmp;
        } catch (Exception e) {
            // 细化 Redis 读取阶段的错误分类
            MetricsErrorRecorder.recordTimeoutIfAny(metrics, streamKey, e);
            MetricsErrorRecorder.recordConnectionIfAny(metrics, streamKey, e);
            MetricsErrorRecorder.recordSerializationIfAny(metrics, streamKey, e);
            // 如果是 NOGROUP 错误，尝试创建并返回让定时器下一轮重试
            if (isNoGroupError(e)) {
                try {
                    log.warn("XREADGROUP 报 NOGROUP，创建 stream/group 后重试: stream={}, group={}", streamKey, group);
                    ensureStreamAndGroup(streamKey, group);
                    return false;
                } catch (Exception createEx) {
                    log.error("创建 stream/group 失败: stream={}, group={}", streamKey, group, createEx);
                }
            }
            throw e;
        }

        if (records == null || records.isEmpty()) {
            log.debug("未拉取到消息: stream={}, group={}, consumer={}", streamKey, group, consumer);
            return false;
        }

        // 发布 Map 载荷，由上层消费者按 Base64 编码方案反序列化为目标类型
        List<StreamMessage<String>> messages = records.stream().map(record -> new StreamMessage<>(
                record.getStream(),
                record.getId(),
                record.getValue()
        )).toList();

        // 记录消息消费指标
        totalMessageCount.addAndGet(messages.size());
        metrics.recordMessageConsumed(streamKey, group);
        // 更新消息计数与活动时间
        String pollerKey = "%s:%s:%s".formatted(streamKey, group, consumer);
        incrementPollerMessageCount(pollerKey, messages.size());
        markPollerActivity(pollerKey);

        log.debug("拉取到消息: stream={}, group={}, consumer={}, count={}",
                streamKey, group, consumer, messages.size());

        for (StreamMessage<String> m : messages) {
            try {
                var event = StreamMessageEvent.<String>builder()
                        .streamKey(m.stream())
                        .group(group)
                        .consumer(consumer)
                        .recordId(m.id())
                        .payload(m.body())
                        .build();
                RedisStreamEventBus.publishMessage(event);

                log.debug("消息发布到事件总线: stream={}, group={}, consumer={}, recordId={}",
                        streamKey, group, consumer, m.id().getValue());

            } catch (Exception e) {
                // 记录错误指标
                totalErrorCount.incrementAndGet();
                metrics.recordMessageFailed(streamKey, group, "event_publish_error");

                log.error("消息发布到事件总线失败: stream={}, group={}, consumer={}, recordId={}",
                        streamKey, group, consumer, m.id().getValue(), e);
            }
        }

        return true; // 拉取到消息
    }

    private boolean isNoGroupError(Exception e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("NOGROUP");
    }

    private boolean isConnectionClosedError(Exception e) {
        if (e == null) return false;

        // 检查异常链中是否包含连接关闭错误
        Throwable cause = e;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && (msg.contains("Connection closed") || msg.contains("Connection reset"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    // =============== 状态快照 ===============

    /**
     * 获取当前活动拉取器数量
     *
     * @return 活动拉取器数量
     */
    public int getActivePollerCount() {
        return activePollers.size();
    }

    /**
     * 获取拉取器状态快照
     *
     * @return 状态快照
     */
    public Map<String, Object> getPollerStatusSnapshot() {
        Map<String, Object> snapshot = new ConcurrentHashMap<>();
        pollerStatusMap.forEach((k, v) -> snapshot.put(k, v.toMap()));
        return snapshot;
    }

    // ApplicationEvent 模式已替换为 Reactor 控制总线

    private void markPollerActivity(String pollerKey) {
        var status = pollerStatusMap.get(pollerKey);
        if (status != null) {
            status.lastActivity = Instant.now();
        }
    }

    private void incrementPollerMessageCount(String pollerKey, int delta) {
        var status = pollerStatusMap.get(pollerKey);
        if (status != null && delta > 0) {
            status.messageCount.addAndGet(delta);
        }
    }

    private void incrementPollerErrorCount(String pollerKey) {
        var status = pollerStatusMap.get(pollerKey);
        if (status != null) {
            status.errorCount.incrementAndGet();
        }
    }

    /**
     * 拉取器配置信息
     */
    private static final class PollerConfig {

        /** Stream 键名 */
        final String streamKey;

        /** 消费者组名 */
        final String group;

        /** 消费者名称 */
        final String consumer;

        /** 单次拉取数量 */
        final int count;

        /** 阻塞等待时间（毫秒） */
        final long blockMs;

        /**
         * 构造拉取器配置
         *
         * @param streamKey Stream 键名
         * @param group     消费者组名
         * @param consumer  消费者名称
         * @param count     单次拉取数量
         * @param blockMs   阻塞等待时间（毫秒）
         */
        PollerConfig(String streamKey, String group, String consumer, int count, long blockMs) {
            this.streamKey = streamKey;
            this.group = group;
            this.consumer = consumer;
            this.count = count;
            this.blockMs = blockMs;
        }
    }

    /** 拉取器运行时状态 */
    private static final class PollerStatus {

        /** Stream 键名 */
        final String streamKey;

        /** 消费者组名 */
        final String group;

        /** 消费者名称 */
        final String consumer;

        /** 是否处于活跃拉取中 */
        volatile boolean active;

        /** 启动时间 */
        final Instant startTime;

        /** 最后活动时间 */
        volatile Instant lastActivity;

        /** 已拉取消息数 */
        final AtomicLong messageCount;

        /** 错误次数 */
        final AtomicLong errorCount;

        /**
         * 构造拉取器状态
         *
         * @param streamKey    Stream 键名
         * @param group        消费者组名
         * @param consumer     消费者名称
         * @param active       是否活跃
         * @param startTime    启动时间
         * @param lastActivity 最后活动时间
         * @param messageCount 消息计数
         * @param errorCount   错误计数
         */
        PollerStatus(String streamKey, String group, String consumer, boolean active,
                     Instant startTime, Instant lastActivity, AtomicLong messageCount, AtomicLong errorCount) {
            this.streamKey = streamKey;
            this.group = group;
            this.consumer = consumer;
            this.active = active;
            this.startTime = startTime;
            this.lastActivity = lastActivity;
            this.messageCount = messageCount;
            this.errorCount = errorCount;
        }

        Map<String, Object> toMap() {
            return Map.of(
                    "streamKey", streamKey,
                    "group", group,
                    "consumer", consumer,
                    "active", active,
                    "startTime", startTime.toString(),
                    "lastActivity", lastActivity.toString(),
                    "messageCount", messageCount.get(),
                    "errorCount", errorCount.get()
            );
        }
    }

    /**
     * 确保目标 Stream 与消费者组存在；如果 Stream 不存在则创建空 Stream，再创建消费者组。
     *
     * @param streamKey Stream 键名
     * @param group    消费者组名
     */
    private void ensureStreamAndGroup(String streamKey, String group) {
        try {
            // 如果 Stream 不存在，创建一个占位消息以便创建 Stream
            boolean streamExists = redisTemplate.hasKey(streamKey);
            if (!streamExists) {
                // 使用 XADD 创建 stream（保留一条占位消息）
                redisTemplate.opsForStream().add(streamKey, Map.of("_init", "true"));
                log.info("已创建 Stream: {}", streamKey);
            }
            // 创建消费者组（如果已存在会抛异常，需忽略）
            try {
                redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0-0"), group);
                log.info("已创建消费者组: {}@{}", group, streamKey);
            } catch (Exception groupEx) {
                // 若组已存在，忽略
                if (groupEx.getCause() != null && groupEx.getCause().getMessage().contains("BUSYGROUP")) {
                    log.debug("消费者组已存在: {}@{}", group, streamKey);
                } else {
                    throw groupEx;
                }
            }
        } catch (Exception ex) {
            log.warn("确保 Stream/Group 存在失败: stream={}, group={}, err={}", streamKey, group, ex.getMessage());
            throw ex;
        }
    }

    /**
     * 停止所有拉取器
     */
    @PreDestroy
    public void shutdown() {
        try {
            log.info("开始优雅停机 Redis Stream Reactor");

            // 设置停机标志 - 这是关键！必须在停止拉取器之前设置
            isShuttingDown.set(true);
            log.info("已设置停机标志，所有新的拉取操作将被跳过");

            // 立即停止所有拉取器 - 取消所有定时任务
            stopAllPollers();

            // 等待一小段时间确保所有定时任务都检测到停机标志
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 取消控制总线订阅，避免资源泄漏
            try {
                if (controlSubscription != null && !controlSubscription.isDisposed()) {
                    controlSubscription.dispose();
                    log.debug("控制总线订阅已取消");
                }
            } catch (Exception ignore) {
                // ignore
            }

            // 关闭调度器
            if (scheduler != null && !scheduler.isShutdown()) {
                log.info("开始关闭调度器");
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                        log.warn("调度器未能及时停止，强制关闭");
                        scheduler.shutdownNow();
                    } else {
                        log.info("调度器已正常关闭");
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                    log.warn("调度器关闭被中断，已强制关闭");
                }
            }

            // 清理状态映射
            pollerStatusMap.clear();

            // 记录关闭统计信息
            log.info("Redis Stream Reactor 优雅停机完成 - 总拉取次数: {}, 总消息数: {}, 总错误数: {}",
                    totalPollCount.get(), totalMessageCount.get(), totalErrorCount.get());

        } catch (Exception e) {
            log.error("Redis Stream Reactor 关闭异常", e);
        }
    }

    /**
     * 获取性能统计信息
     *
     * @return 性能统计信息
     */
    public Map<String, Object> getPerformanceStats() {
        return Map.of(
                "totalPollCount", totalPollCount.get(),
                "totalMessageCount", totalMessageCount.get(),
                "totalErrorCount", totalErrorCount.get(),
                "averageMessagesPerPoll", totalPollCount.get() > 0 ?
                        (double) totalMessageCount.get() / totalPollCount.get() : 0.0,
                "errorRate", totalPollCount.get() > 0 ?
                        (double) totalErrorCount.get() / totalPollCount.get() : 0.0
        );
    }

}


