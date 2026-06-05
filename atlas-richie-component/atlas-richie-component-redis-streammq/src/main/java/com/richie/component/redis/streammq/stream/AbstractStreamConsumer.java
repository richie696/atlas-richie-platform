package com.richie.component.redis.streammq.stream;

import com.richie.context.utils.data.Collections;
import com.richie.context.utils.data.JsonUtils;
import com.richie.component.redis.streammq.function.StreamFunction;
import com.richie.contract.model.BaseStreamMessage;
import com.richie.component.redis.streammq.config.stream.RedisStreamProperties;
import com.richie.component.redis.streammq.monitor.MetricsErrorRecorder;
import com.richie.component.redis.streammq.monitor.RedisStreamBacklogMonitor;
import com.richie.component.redis.streammq.monitor.RedisStreamMetrics;
import com.richie.component.redis.streammq.tracing.RedisStreamTracingUtils;
import com.richie.component.redis.streammq.tracing.TraceableMessageWrapper;
import com.richie.component.redis.streammq.utils.DeadLetterQueueUtil;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.richie.component.redis.streammq.config.stream.RedisStreamProperties.ConsumerConfig;
import static com.richie.component.redis.streammq.config.stream.RedisStreamProperties.ErrorStrategy;

/**
 * Redis Stream 消费者抽象基类
 *
 * <p>基于 Reactor 的异步消费框架，提供自动启动/停止、类型转换、并发处理、错误策略与自动确认等能力。
 *
 * <p>主要功能：
 * <ul>
 *   <li>从配置与注解自动初始化消费者参数</li>
 *   <li>启动内部拉取器，订阅消息事件流并并发消费</li>
 *   <li>内置 Micrometer 指标与 OpenTelemetry 链路追踪</li>
 *   <li>幂等保护、死信队列工具方法</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * &#64;RedisStreamConsumer("order-events")
 * public class OrderEventConsumer extends AbstractStreamConsumer<OrderEvent> {
 *
 *     &#64;Override
 *     protected void handle(OrderEvent payload, EventContext ctx) throws Exception {
 *         // 处理订单事件
 *     }
 * }
 * }</pre>
 *
 * @param <T> 消息负载类型，必须继承自 BaseStreamMessage
 * @author richie696
 * @since 2025-09-15
 */
@Slf4j
public abstract class AbstractStreamConsumer<T extends BaseStreamMessage> {

    private static final long POLLING_INTERVAL = 2000;

    /**
     * 消费者配置选项类（已迁移到配置属性方式）
     *
     * @param <T> 消息负载类型
     */
    public static final class Options<T> {

        /** Stream 键名 */
        private final String streamKey;

        /** 消费者组名 */
        private final String group;

        /** 消费者名称 */
        private final String consumer;

        /** 消息负载类型 */
        private final Class<T> targetType;

        /** 并发处理数 */
        private final int concurrency;

        /** 单次拉取消息数量 */
        private final int count;

        /** 是否自动确认消息 */
        private final boolean autoAck;

        /** 是否自动启动 */
        private final boolean autoStart;

        /** 错误处理策略 */
        private final ErrorStrategy errorStrategy;

        private Options(Builder<T> b) {
            this.streamKey = b.streamKey;
            this.group = b.group;
            this.consumer = b.consumer;
            this.targetType = b.targetType;
            this.concurrency = b.concurrency;
            this.count = b.count;
            this.autoAck = b.autoAck;
            this.errorStrategy = b.errorStrategy;
            this.autoStart = b.autoStart;
        }

        /**
         * 创建配置建造者
         *
         * @param <T>  消息负载类型
         * @param type 消息负载类型的 Class 对象
         * @return 配置建造者实例
         */
        public static <T> Builder<T> builder(Class<T> type) {
            return new Builder<>(type);
        }

        /**
         * 配置建造者类
         *
         * @param <T> 消息负载类型
         */
        public static final class Builder<T> {

            /** 消息负载类型 */
            private final Class<T> targetType;

            /** Stream 键名 */
            private String streamKey;

            /** 消费者组名 */
            private String group;

            /** 消费者名称 */
            private String consumer = "default-consumer";

            /** 并发处理数（默认 CPU 核心数的一半，至少 1） */
            private int concurrency = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);

            /** 单次拉取消息数量（默认 1） */
            private int count = 1;

            /** 是否自动确认消息（默认 true） */
            private boolean autoAck = true;

            /** 是否自动启动（默认 true） */
            private boolean autoStart = true;

            /** 错误处理策略（默认 SKIP） */
            private ErrorStrategy errorStrategy = ErrorStrategy.SKIP;

            private Builder(Class<T> type) {
                this.targetType = type;
            }

            /**
             * 设置 Stream 键名
             *
             * @param v Stream 键名
             * @return 当前建造者
             */
            public Builder<T> streamKey(String v) {
                this.streamKey = v;
                return this;
            }

            /**
             * 设置消费者组名
             *
             * @param v 消费者组名
             * @return 当前建造者
             */
            public Builder<T> group(String v) {
                this.group = v;
                return this;
            }

            /**
             * 设置消费者名称
             *
             * @param v 消费者名称
             * @return 当前建造者
             */
            public Builder<T> consumer(String v) {
                this.consumer = v;
                return this;
            }

            /**
             * 设置并发处理数
             *
             * @param v 并发数
             * @return 当前建造者
             */
            public Builder<T> concurrency(int v) {
                this.concurrency = v;
                return this;
            }

            /**
             * 设置单次拉取消息数量
             *
             * @param v 单次拉取数量
             * @return 当前建造者
             */
            public Builder<T> count(int v) {
                this.count = v;
                return this;
            }

            /**
             * 设置是否自动确认消息
             *
             * @param v 是否自动确认
             * @return 当前建造者
             */
            public Builder<T> autoAck(boolean v) {
                this.autoAck = v;
                return this;
            }

            /**
             * 设置错误处理策略
             *
             * @param v 错误处理策略
             * @return 当前建造者
             */
            public Builder<T> errorStrategy(ErrorStrategy v) {
                this.errorStrategy = v;
                return this;
            }

            /**
             * 设置是否自动启动
             *
             * @param v 是否自动启动
             * @return 当前建造者
             */
            public Builder<T> autoStart(boolean v) {
                this.autoStart = v;
                return this;
            }

            /**
             * 构建配置对象
             *
             * @return 消费者配置选项
             */
            public Options<T> build() {
                return new Options<>(this);
            }
        }
    }

    /**
     * 消费者配置选项（旧方式，已废弃）
     */
    private Options<T> options;

    /**
     * 消费者配置属性（新方式）
     */
    @Autowired
    private RedisStreamProperties consumerProperties;

    /**
     * 监控指标收集器
     */
    @Autowired
    private RedisStreamMetrics metrics;
    /**
     * Redis Stream 拉取器
     */
    @Autowired
    private RedisStreamReactor reactor;
    /**
     * 积压监控器
     */
    @Autowired
    private RedisStreamBacklogMonitor backlogMonitor;

    /** Stream 操作函数 */
    @Autowired
    private StreamFunction stream;

    /** 幂等防护 */
    @Autowired
    private RedisStreamIdempotencyGuard idempotencyGuard;

    /** 幂等窗口（可后续改为从配置读取） */
    private final Duration idempotencyTtl = Duration.ofHours(24);

    /** 响应式订阅对象，用于控制消费者的生命周期 */
    private Disposable subscription;

    /** 已处理消息数 */
    private final AtomicLong processedCount = new AtomicLong(0);

    /** 处理失败数 */
    private final AtomicLong errorCount = new AtomicLong(0);

    /** 重试次数 */
    private final AtomicLong retryCount = new AtomicLong(0);

    /** 是否正在优雅停机 */
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    /**
     * 构造函数（旧方式，已废弃）
     *
     * @param options 消费者配置选项
     * @throws NullPointerException 如果配置或必要参数为 null
     */
    protected AbstractStreamConsumer(Options<T> options) {
        this.options = Objects.requireNonNull(options, "The options must not be null");

        // 验证必要的配置参数
        Objects.requireNonNull(options.streamKey, "The options.streamKey must not be null");
        Objects.requireNonNull(options.group, "The options.group must not be null");
        Objects.requireNonNull(options.targetType, "The options.targetType must not be null");

        log.debug("AbstractStreamConsumer 初始化: streamKey={}, group={}, targetType={}",
                options.streamKey, options.group, options.targetType.getSimpleName());
    }

    /**
     * 默认构造函数（新方式）
     *
     * <p>使用注解和配置属性方式，无需手动传入配置
     */
    protected AbstractStreamConsumer() {
        // 配置将在 @PostConstruct 方法中从注解和配置属性中获取
    }

    /**
     * Spring 容器初始化后自动调用
     *
     * <p>从注解和配置属性中获取配置，然后启动消费者
     */
    @PostConstruct
    protected void initializeFromConfig() {
        // 从注解获取配置名称
        RedisStreamConsumer annotation = this.getClass().getAnnotation(RedisStreamConsumer.class);
        if (annotation == null) {
            // 如果没有注解，尝试使用旧方式
            if (options != null && options.autoStart) {
                start();
            }
            return;
        }

        String configName = annotation.value();

        // 从配置中获取参数
        ConsumerConfig config =
            consumerProperties.getConfigs().get(configName);

        if (config == null) {
            throw new IllegalStateException("未找到消费者配置: %s".formatted(configName));
        }

        // 构建 Options（兼容旧代码）
        // 如果 targetType 为空且是死信队列配置，则使用 DeadLetterMessage 作为默认类型
        Class<? extends BaseStreamMessage> targetType = config.getTargetType();
        if (targetType == null && isDeadLetterQueueConfig(configName)) {
            targetType = DeadLetterQueueUtil.DeadLetterMessage.class;
            log.debug("死信队列配置 {} 使用默认 targetType: DeadLetterMessage", configName);
        }

        this.options = Options.builder((Class<T>) targetType)
            .streamKey(config.getStreamKey())
            .group(config.getGroup())
            .consumer(config.getConsumer())
            .autoAck(config.isAutoAck())
            .concurrency(config.getConcurrency())
            .count(config.getCount())
            .errorStrategy(config.getErrorStrategy())
            .autoStart(config.isAutoStart())
            .build();

        log.debug("AbstractStreamConsumer 从配置初始化: streamKey={}, group={}, targetType={}, count={}, concurrency={}",
                options.streamKey, options.group, options.targetType.getSimpleName(), options.count, options.concurrency);

        // 如果配置了自动启动，则启动消费者
        if (options.autoStart) {
            start();
        }
    }

    /**
     * 判断是否为死信队列配置
     *
     * @param configName 配置名称
     * @return 是否为死信队列配置
     */
    private boolean isDeadLetterQueueConfig(String configName) {
        return configName != null && (
            configName.startsWith("dlq-") ||
            configName.startsWith("dlq:") ||
            configName.contains("dead-letter") ||
            configName.contains("deadletter")
        );
    }

    /**
     * 启动消费者
     *
     * <p>创建响应式流订阅，开始监听和处理 Redis Stream 消息
     *
     * <p>处理流程：
     * <ol>
     *   <li>启动 Redis Stream 拉取器</li>
     *   <li>过滤指定 streamKey 的消息</li>
     *   <li>在弹性线程池中处理消息</li>
     *   <li>类型检查和转换</li>
     *   <li>调用具体的处理方法</li>
     *   <li>根据配置确认消息</li>
     *   <li>错误处理</li>
     * </ol>
     */
    public synchronized void start() {
        // 防止重复启动
        if (subscription != null && !subscription.isDisposed()) return;

        // 增加活跃消费者计数
        metrics.incrementActiveConsumers();

        // 启动 Redis Stream 拉取器（使用 count 配置单次拉取消息数量）
        reactor.startPolling(options.streamKey, options.group, options.consumer, options.count, POLLING_INTERVAL, options.targetType);

        // 添加 Stream 到积压监控
        backlogMonitor.addMonitoredStream(options.streamKey);

        // 获取消息流
        Flux<StreamMessageEvent<?>> source = stream.messageFlow();
        subscription = source
                // 过滤出目标 stream 的消息
                .filter(e -> options.streamKey.equals(e.streamKey()))
                // 在弹性线程池中处理，避免阻塞 I/O 线程
                .publishOn(Schedulers.boundedElastic())
                // 并发处理消息
                .flatMap(e -> {
                    // payload 现在是 Base64 编码的字符串
                    String dataBase64 = (String) e.payload();

                    // 处理包装的消息（支持链路追踪）
                    T casted;
                    TraceableMessageWrapper messageWrapper;

                    try {
                        if (dataBase64 == null || dataBase64.equals("null")) {
                            return Flux.empty();
                        }

                        // 解码 Base64 并反序列化
                        byte[] messageBytes = Base64.getDecoder().decode(dataBase64);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> decodedMessageData = JsonUtils.getInstance().deserializePayload(messageBytes, Map.class);
                        if (decodedMessageData == null) {
                            return Flux.empty();
                        }
                        // 提取业务负载
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payloadObj = (Map<String, Object>) decodedMessageData.get("payload");
                        if (payloadObj == null) {
                            return Flux.empty();
                        }

                        // 反序列化业务负载为目标类型
                        T deserialized = JsonUtils.getInstance().convertObject(payloadObj, options.targetType);
                        casted = deserialized;

                        // 提取追踪上下文
                        Map<String, String> traceContext = decodedMessageData.entrySet().stream()
                                .filter(entry -> entry.getKey().startsWith("trace") || entry.getKey().equals("sampled"))
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        entry -> String.valueOf(entry.getValue())
                                ));
                        messageWrapper = TraceableMessageWrapper.wrapForConsume(deserialized, traceContext);

                    } catch (Exception ex) {
                        // 无法反序列化则丢弃该消息
                        log.warn("反序列化Base64消息失败，丢弃消息: type={}, error={}", options.targetType.getSimpleName(), ex.getMessage());
                        return Flux.empty();
                    }

                    // 设置 StreamFunction 实例到 EventContext
                    EventContext.setStreamFunction(stream);

                    // 创建事件上下文，包含确认消息所需的信息
                    EventContext ctx = new EventContext(e.streamKey(), e.group(), e.recordId());

                    // 延迟执行处理逻辑，支持异常处理
                    return Flux.defer(() -> {

                        // 记录本条消息从进入业务处理到结束的起始时间（用于计算处理耗时）
                        long startTime = System.currentTimeMillis();

                        // 幂等去重：优先业务键（如覆盖在子类中提供），否则使用 recordId
                        String idempotencyKey = buildIdempotencyKey(casted, e.recordId().getValue());
                        if (!idempotencyGuard.tryAcquire(idempotencyKey, idempotencyTtl)) {
                            log.info("幂等去重命中，跳过处理: key={}, stream={}, group={}, recordId={}", idempotencyKey, e.streamKey(), e.group(), e.recordId().getValue());
                            // 已处理则直接 ACK
                            if (options.autoAck) {
                                ctx.ack();
                                metrics.recordMessageAcknowledged(options.streamKey, options.group);
                            }
                            return Flux.empty();
                        }

                        // 创建消费者 Span（支持链路追踪）
                        // 创建用于链路追踪的 Scope（消费者 Span），优先复用消息携带的上游追踪上下文
                        RedisStreamTracingUtils.TracingScope tracingScope;
                        if (messageWrapper.hasValidTraceContext()) {
                            tracingScope = RedisStreamTracingUtils.createConsumerSpan(messageWrapper, options.streamKey, options.group, "redis.stream.consume");
                        } else {
                            tracingScope = RedisStreamTracingUtils.createNewSpan("redis.stream.consume");
                        }

                        // Micrometer 计时样本：用于向带标签的 Timer（按 stream 维度）上报本次处理耗时
                        // 是否创建样本由采样与配置开关共同控制，避免高并发下的开销
                        Timer.Sample sample = null;
                        if (metrics.shouldRecordProcessingTimerSample()) {
                            sample = Timer.start(metrics.getMeterRegistry());
                        }

                        // 使用 try-with-resources 确保 Scope 正确关闭与 Span 正确结束
                        try (RedisStreamTracingUtils.TracingScope scope = tracingScope) {
                            // 设置 Span 属性
                            if (scope != null && scope.getSpan() != null) {
                                // 在 Span 上标注与本次处理相关的关键信息，便于在追踪系统中检索与聚合
                                scope.getSpan().setAttribute("stream.key", options.streamKey);
                                scope.getSpan().setAttribute("consumer.group", options.group);
                                scope.getSpan().setAttribute("message.type", casted.getClass().getSimpleName());

                                // 如果有追踪上下文，添加追踪信息
                                if (messageWrapper != null) {
                                    // 如果消息中携带了追踪上下文，则把 traceId/spanId 等透传到当前 Span
                                    scope.getSpan().setAttribute("message.traceId", messageWrapper.getTraceId());
                                    scope.getSpan().setAttribute("message.spanId", messageWrapper.getSpanId());
                                    scope.getSpan().setAttribute("message.sampled", messageWrapper.isSampled());
                                }
                            }

                            // 调用子类实现的具体处理逻辑
                            handle(casted, ctx);

                            // 记录成功处理指标
                            processedCount.incrementAndGet();
                            metrics.recordMessageConsumed(options.streamKey, options.group);

                            // 如果配置了自动确认，则确认消息处理完成
                            if (options.autoAck) {
                                // 调用 ack 确认消息，避免重复消费
                                ctx.ack();
                                metrics.recordMessageAcknowledged(options.streamKey, options.group);
                            }

                            // 记录处理耗时
                            long duration = System.currentTimeMillis() - startTime;
                            // 上报一条“无标签/全局”处理耗时统计，用于总体观测（与带标签 Timer 互补）
                            metrics.recordProcessingDuration(options.streamKey, Duration.ofMillis(duration));

                            // 添加 Span 属性
                            if (scope != null && scope.getSpan() != null) {
                                // 在 Span 中记录处理耗时与成功标记，便于 APM 中快速定位慢处理与失败
                                scope.getSpan().setAttribute("processing.duration", duration);
                                scope.getSpan().setAttribute("processing.success", true);
                            }

                            log.debug("消息处理成功: streamKey={}, group={}, duration={}ms, traceId={}",
                                    options.streamKey, options.group, duration,
                                    messageWrapper != null ? messageWrapper.getTraceId() : "N/A");

                            return Flux.empty();

                        } catch (Exception ex) {
                            // 记录错误指标
                            // 本分支用于捕获业务处理过程中的异常，并进行指标与追踪补录
                            errorCount.incrementAndGet();
                            metrics.recordMessageFailed(options.streamKey, options.group, ex.getClass().getSimpleName());
                            // 若与 Redis 交互导致的异常，进行细分记录（若无 Redis 交互可忽略）
                            MetricsErrorRecorder.recordTimeoutIfAny(metrics, options.streamKey, ex);
                            MetricsErrorRecorder.recordConnectionIfAny(metrics, options.streamKey, ex);
                            MetricsErrorRecorder.recordSerializationIfAny(metrics, options.streamKey, ex);

                            // 记录错误到 Span
                            if (tracingScope != null && tracingScope.getSpan() != null) {
                                // 将异常信息记录到 Span，便于在链路视图中回溯问题
                                RedisStreamTracingUtils.recordError(tracingScope.getSpan(), ex);
                                tracingScope.getSpan().setAttribute("processing.success", false);
                                tracingScope.getSpan().setAttribute("error.type", ex.getClass().getSimpleName());
                            }

                            // 调用业务错误处理（子类可覆盖）
                            onError(ex, casted, ctx);

                            // 根据错误策略处理
                            if (options.errorStrategy == ErrorStrategy.SKIP) {
                                // 跳过错误消息，继续处理下一条
                                log.warn("跳过错误消息: streamKey={}, group={}, error={}, traceId={}",
                                        options.streamKey, options.group, ex.getMessage(),
                                        messageWrapper != null ? messageWrapper.getTraceId() : "N/A");
                                return Flux.empty();

                            } else if (options.errorStrategy == ErrorStrategy.RETRY) {
                                // 简单重试一次（可扩展为指数退避）
                                // 注意：重试成功与失败分别上报不同的指标/事件，便于观测重试有效性
                                retryCount.incrementAndGet();
                                metrics.recordMessageRetried(options.streamKey, options.group);

                                try {
                                    handle(casted, ctx);
                                    if (options.autoAck) {
                                        // 重试成功后同样需要确认消息
                                        ctx.ack();
                                        metrics.recordMessageAcknowledged(options.streamKey, options.group);
                                    }

                                    // 记录重试成功到 Span
                                    if (tracingScope != null && tracingScope.getSpan() != null) {
                                        tracingScope.getSpan().addEvent("retry.success");
                                    }

                                    log.info("消息重试成功: streamKey={}, group={}, traceId={}",
                                            options.streamKey, options.group,
                                            messageWrapper != null ? messageWrapper.getTraceId() : "N/A");

                                } catch (Exception retryEx) {
                                    // 重试失败，记录错误
                                    errorCount.incrementAndGet();
                                    metrics.recordMessageFailed(options.streamKey, options.group, "retry_failed");

                                    // 记录重试失败到 Span
                                    if (tracingScope != null && tracingScope.getSpan() != null) {
                                        tracingScope.getSpan().addEvent("retry.failed");
                                        RedisStreamTracingUtils.recordError(tracingScope.getSpan(), retryEx);
                                    }

                                    log.error("消息重试失败: streamKey={}, group={}, error={}, traceId={}",
                                            options.streamKey, options.group, retryEx.getMessage(),
                                            messageWrapper != null ? messageWrapper.getTraceId() : "N/A");
                                }
                                return Flux.empty();

                            } else { // NO_ACK
                                // 不确认消息，留待后续处理
                                log.warn("消息未确认，留待后续处理: streamKey={}, group={}, error={}, traceId={}",
                                        options.streamKey, options.group, ex.getMessage(),
                                        messageWrapper != null ? messageWrapper.getTraceId() : "N/A");
                                return Flux.empty();
                            }
                        } finally {
                            // 若开启了带标签计时器的采样与记录，则将本次处理耗时样本写入到
                            // redis.stream.processing.duration{stream=...} 对应的 Timer 中
                            if (sample != null) {
                                sample.stop(metrics.getProcessingDurationTimer(options.streamKey));
                            }
                        }
                    });
                }, options.concurrency) // 设置并发处理数
                .subscribe(); // 开始订阅和处理
    }

    /**
     * 停止消费者
     *
     * <p>Spring 容器销毁前自动调用，释放订阅资源
     */
    @PreDestroy
    public synchronized void stop() {
        try {
            log.info("开始优雅停机 Redis Stream Consumer: streamKey={}, group={}",
                    options.streamKey, options.group);

            // 设置停机标志
            isShuttingDown.set(true);

            // 停止消息订阅
            if (subscription != null && !subscription.isDisposed()) {
                subscription.dispose(); // 取消订阅
                subscription = null;

                // 减少活跃消费者计数
                metrics.decrementActiveConsumers();
            }

            // 停止 Redis Stream 拉取器
            reactor.stopPolling(options.streamKey, options.group, options.consumer);

            // 从积压监控中移除 Stream
            backlogMonitor.removeMonitoredStream(options.streamKey);

            // 等待一段时间确保所有正在处理的消息完成
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("等待消息处理完成时被中断");
            }

            // 清理统计信息（可选）
            // processedCount.set(0);
            // errorCount.set(0);
            // retryCount.set(0);

            log.info("Redis Stream Consumer 优雅停机完成: streamKey={}, group={}, 处理消息数: {}, 错误数: {}",
                    options.streamKey, options.group, processedCount.get(), errorCount.get());

        } catch (Exception e) {
            log.error("Redis Stream Consumer 优雅停机失败: streamKey={}, group={}",
                    options.streamKey, options.group, e);
        }
    }

    /**
     * 处理消息的抽象方法
     *
     * <p>子类必须实现此方法来定义具体的消息处理逻辑
     *
     * @param payload 消息负载
     * @param ctx     事件上下文，包含确认消息等操作
     * @throws Exception 处理过程中的异常
     */
    protected abstract void handle(T payload, EventContext ctx) throws Exception;

    /**
     * 错误处理方法
     *
     * <p>当消息处理发生异常时调用，子类可以覆盖此方法来实现错误处理逻辑
     * <p>包括：记录日志、发送到死信队列、发送告警通知等
     * <p>框架提供死信队列发送工具，但由子类决定是否使用
     *
     * @param e       异常对象
     * @param payload 发生错误的消息负载
     * @param ctx     事件上下文
     */
    protected void onError(Throwable e, T payload, EventContext ctx) {
        // 默认实现：记录错误日志
        log.error("消息处理异常: streamKey={}, group={}, error={}",
                options.streamKey, options.group, e.getMessage(), e);
    }

    /**
     * 生成幂等键：默认使用 recordId，可由具体子类覆写提供业务唯一键。
     *
     * @param payload  消息负载
     * @param recordId 记录 ID
     * @return 幂等键
     */
    protected String buildIdempotencyKey(T payload, String recordId) {
        return recordId;
    }

    /**
     * 发送消息到死信队列（工具方法）
     *
     * <p>框架提供此工具方法，子类可以在 onError 方法中调用
     * <p>子类可以决定是否发送到死信队列，以及使用什么策略
     *
     * @param payload 发生错误的消息负载
     * @param error 异常对象
     * @param ctx 事件上下文
     * @return 是否发送成功
     */
    protected boolean sendToDeadLetterQueue(T payload, Throwable error, EventContext ctx) {
        try {
            // 使用默认策略发送到死信队列
            DeadLetterQueueUtil.sendToDeadLetterQueue(payload, error, ctx, this.getClass());

            log.debug("消息已发送到死信队列: streamKey={}, group={}, recordId={}, messageType={}",
                    ctx.streamKey(), ctx.group(), ctx.recordId(), payload.getClass().getSimpleName());
            return true;

        } catch (Exception e) {
            // 死信队列发送失败，记录严重错误
            log.error("发送到死信队列失败: streamKey={}, group={}, recordId={}, error={}",
                    ctx.streamKey(), ctx.group(), ctx.recordId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 发送消息到死信队列（指定策略）
     *
     * <p>框架提供此工具方法，子类可以在 onError 方法中调用
     * <p>子类可以决定是否发送到死信队列，以及使用什么策略
     *
     * @param payload 发生错误的消息负载
     * @param error 异常对象
     * @param ctx 事件上下文
     * @param strategy 死信队列策略
     * @return 是否发送成功
     */
    protected boolean sendToDeadLetterQueue(T payload, Throwable error, EventContext ctx,
                                          DeadLetterQueueUtil.DeadLetterStrategy strategy) {
        try {
            // 使用指定策略发送到死信队列
            DeadLetterQueueUtil.sendToDeadLetterQueue(payload, error, ctx, this.getClass(), strategy);

            log.debug("消息已发送到死信队列: streamKey={}, group={}, recordId={}, messageType={}, strategy={}",
                    ctx.streamKey(), ctx.group(), ctx.recordId(), payload.getClass().getSimpleName(), strategy);
            return true;

        } catch (Exception e) {
            // 死信队列发送失败，记录严重错误
            log.error("发送到死信队列失败: streamKey={}, group={}, recordId={}, strategy={}, error={}",
                    ctx.streamKey(), ctx.group(), ctx.recordId(), strategy, e.getMessage(), e);
            return false;
        }
    }

    // ==================== 监控相关方法 ====================

    /**
     * 获取消费者统计信息
     *
     * @return 统计信息
     */
    public Map<String, Object> getConsumerStats() {
        return Collections.mapOf(
                "streamKey", options.streamKey,
                "group", options.group,
                "targetType", options.targetType.getSimpleName(),
                "concurrency", options.concurrency,
                "autoAck", options.autoAck,
                "errorStrategy", options.errorStrategy.name(),
                "processedCount", processedCount.get(),
                "errorCount", errorCount.get(),
                "retryCount", retryCount.get(),
                "isActive", subscription != null && !subscription.isDisposed(),
                "errorRate", processedCount.get() > 0 ?
                        (double) errorCount.get() / processedCount.get() : 0.0
        );
    }

    /**
     * 获取处理的消息数量
     *
     * @return 处理的消息数量
     */
    public long getProcessedCount() {
        return processedCount.get();
    }

    /**
     * 获取错误数量
     *
     * @return 错误数量
     */
    public long getErrorCount() {
        return errorCount.get();
    }

    /**
     * 获取重试数量
     *
     * @return 重试数量
     */
    public long getRetryCount() {
        return retryCount.get();
    }

    /**
     * 获取错误率
     *
     * @return 错误率（0.0-1.0）
     */
    public double getErrorRate() {
        long total = processedCount.get();
        return total > 0 ? (double) errorCount.get() / total : 0.0;
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        processedCount.set(0);
        errorCount.set(0);
        retryCount.set(0);
        log.info("消费者统计信息已重置: streamKey={}, group={}", options.streamKey, options.group);
    }

}
