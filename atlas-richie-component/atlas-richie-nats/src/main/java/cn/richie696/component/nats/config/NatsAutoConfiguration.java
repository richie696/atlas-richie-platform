/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.nats.config;

import cn.richie696.component.nats.NatsComponent;
import cn.richie696.component.nats.bus.JetStreamBus;
import cn.richie696.component.nats.bus.NatsBus;
import cn.richie696.component.nats.bus.NatsEndpoint;
import cn.richie696.component.nats.connection.JetStreamManagementService;
import cn.richie696.component.nats.connection.NatsConnectionManager;
import cn.richie696.component.nats.dlq.NatsDeadLetterAdvisoryConsumer;
import cn.richie696.component.nats.dlq.NatsDeadLetterPublisher;
import cn.richie696.component.nats.pipeline.NatsSubscriberFactory;
import cn.richie696.component.nats.strategy.*;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.util.Set;

/**
 * NATS 组件自动配置
 *
 * <p>根据 {@code platform.component.nats.*} 配置属性按需装配所有 Bean。
 * 策略接口均提供默认实现，用户可通过自定义 Bean 替换。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(NatsProperties.class)
@ConditionalOnProperty(name = "platform.component.nats.enabled", havingValue = "true", matchIfMissing = true)
public class NatsAutoConfiguration {

    // ==================== L1/L2 策略实现 ====================

    /**
     * 注册消息序列化策略 Bean。默认使用 Jackson 实现，业务方可通过自定义
     * {@link NatsMessageSerializer} Bean 替换。
     *
     * @return 默认的 Jackson 序列化器实例
     */
    @Bean
    @ConditionalOnMissingBean(NatsMessageSerializer.class)
    public NatsMessageSerializer natsMessageSerializer() {
        return new JacksonNatsMessageSerializer();
    }

    /**
     * 注册 header 注入策略 Bean。仅在 {@code platform.component.nats.header-propagation.enabled}
     * 为 true 时将白名单传入实现，否则传入空集合（即注入器实际不做任何复制）。
     *
     * @param properties NATS 配置属性，用于读取 header 透传白名单
     * @return 默认 {@link DefaultNatsHeaderInjector} 实例
     */
    @Bean
    @ConditionalOnMissingBean(NatsHeaderInjector.class)
    public NatsHeaderInjector natsHeaderInjector(NatsProperties properties) {
        var propagation = properties.getHeaderPropagation();
        return new DefaultNatsHeaderInjector(
                propagation.isEnabled() ? propagation.getHeaders() : Set.of());
    }

    /**
     * 注册 header 抽取策略 Bean。与 {@link #natsHeaderInjector} 配对，
     * 在订阅端按相同白名单将消息 header 还原到 MDC。
     *
     * @param properties NATS 配置属性，用于读取 header 透传白名单
     * @return 默认 {@link DefaultNatsHeaderExtractor} 实例
     */
    @Bean
    @ConditionalOnMissingBean(NatsHeaderExtractor.class)
    public NatsHeaderExtractor natsHeaderExtractor(NatsProperties properties) {
        var propagation = properties.getHeaderPropagation();
        return new DefaultNatsHeaderExtractor(
                propagation.isEnabled() ? propagation.getHeaders() : Set.of());
    }

    /**
     * 注册链路追踪策略 Bean。默认基于 OpenTelemetry；总开关由
     * {@code platform.component.nats.tracing.enabled} 控制。
     *
     * @param properties NATS 配置属性，用于读取 tracing 总开关
     * @return OpenTelemetry 实现的追踪支撑实例
     */
    @Bean
    @ConditionalOnMissingBean(NatsTracingSupport.class)
    public NatsTracingSupport natsTracingSupport(NatsProperties properties) {
        return new OpenTelemetryNatsTracingSupport(properties.getTracing().isEnabled());
    }

    /**
     * 注册幂等去重策略 Bean。仅在 {@code platform.component.nats.idempotent.enabled=true}
     * 时启用；按 {@code idempotent.datasource} 选择 Redis 或内存实现。
     *
     * @param properties NATS 配置属性，用于选择数据源
     * @return 选中的 {@link NatsIdempotentChecker} 实现
     */
    @Bean
    @ConditionalOnMissingBean(NatsIdempotentChecker.class)
    @ConditionalOnProperty(name = "platform.component.nats.idempotent.enabled", havingValue = "true")
    public NatsIdempotentChecker natsIdempotentChecker(NatsProperties properties) {
        return switch (properties.getIdempotent().getDatasource().toLowerCase()) {
            case "redis" -> redisIdempotentChecker(properties);
            default -> new MemoryNatsIdempotentChecker();
        };
    }

    /**
     * 幂等去重未启用时的默认 Bean，保证 {@code NatsSubscriberFactory} 构造时
     * 一定能拿到 {@link NatsIdempotentChecker}，具体是否真正使用由
     * {@code Idempotent.enabled} 决定。
     *
     * @return 兜底的内存实现 Bean
     */
    @Bean
    @ConditionalOnMissingBean(NatsIdempotentChecker.class)
    @ConditionalOnProperty(name = "platform.component.nats.idempotent.enabled", havingValue = "false", matchIfMissing = true)
    public NatsIdempotentChecker noopNatsIdempotentChecker() {
        // 幂等去重未启用时返回内存实现（SubscriberFactory 根据 enabled 标志决定是否使用）
        return new MemoryNatsIdempotentChecker();
    }

    /**
     * 注册错误处理策略 Bean。默认实现 {@link DefaultNatsErrorStrategy}，
     * 业务方可通过自定义 {@link NatsErrorStrategy} Bean 替换。
     *
     * @return 默认错误策略实现实例
     */
    @Bean
    @ConditionalOnMissingBean(NatsErrorStrategy.class)
    public NatsErrorStrategy natsErrorStrategy() {
        return new DefaultNatsErrorStrategy();
    }

    // ==================== L3 基础设施 ====================

    /**
     * 注册 NATS 连接管理器 Bean。封装底层 {@link io.nats.client.Connection} 的
     * 生命周期（创建 / 重连 / 关闭），所有上层 Bus / Endpoint 都基于它获取连接。
     *
     * @param properties NATS 配置属性
     * @return NATS 连接管理器实例
     */
    @Bean
    @ConditionalOnMissingBean(NatsConnectionManager.class)
    public NatsConnectionManager natsConnectionManager(NatsProperties properties) {
        return new NatsConnectionManager(properties);
    }

    /**
     * 注册 JetStream 管理服务 Bean。仅在 {@code platform.component.nats.jetstream.enabled=true}
     * 时启用，提供 Stream / Consumer 的声明与查询能力。
     *
     * @param connectionManager NATS 连接管理器
     * @return JetStream 管理服务实例
     */
    @Bean
    @ConditionalOnMissingBean(JetStreamManagementService.class)
    @ConditionalOnProperty(name = "platform.component.nats.jetstream.enabled", havingValue = "true")
    public JetStreamManagementService jetStreamManagementService(NatsConnectionManager connectionManager) {
        return new JetStreamManagementService(connectionManager);
    }

    /**
     * 当 JetStream 未启用时的 No-Op 管理服务（仅供 NatsComponent 构造用）。
     *
     * <p>为何需要该 Bean：{@link NatsComponent} 的构造器依赖 {@link JetStreamManagementService}，
     * 即使业务方禁用 JetStream 也必须存在一个 Bean，否则装配失败。</p>
     *
     * @param connectionManager NATS 连接管理器
     * @return 兜底的 JetStream 管理服务实例
     */
    @Bean
    @ConditionalOnMissingBean(JetStreamManagementService.class)
    public JetStreamManagementService noopJetStreamManagementService(NatsConnectionManager connectionManager) {
        return new JetStreamManagementService(connectionManager);
    }

    // ==================== L4 管道 ====================

    /**
     * 注册订阅工厂 Bean。组装所有订阅期需要的横切能力（追踪 / header 抽取 / 幂等），
     * 并把 {@code idempotent.enabled} 与 {@code ttl} 透传给工厂。
     *
     * @param tracingSupport    追踪支撑策略
     * @param headerExtractor   header 抽取策略
     * @param idempotentChecker 幂等去重策略（兜底 Bean 由 {@link #noopNatsIdempotentChecker()} 保证非空）
     * @param properties        NATS 配置属性，用于读取幂等开关与 TTL
     * @return 配置完成的订阅工厂实例
     */
    @Bean
    @ConditionalOnMissingBean(NatsSubscriberFactory.class)
    public NatsSubscriberFactory natsSubscriberFactory(NatsTracingSupport tracingSupport,
                                                       NatsHeaderExtractor headerExtractor,
                                                       NatsIdempotentChecker idempotentChecker,
                                                       NatsProperties properties) {
        return new NatsSubscriberFactory(
                tracingSupport,
                headerExtractor,
                idempotentChecker,
                properties.getIdempotent().isEnabled(),
                properties.getIdempotent().getTtl()
        );
    }

    // ==================== L5 门面 ====================

    /**
     * 注册 Core NATS 消息总线 Bean。负责 publish / subscribe / request / reply
     * 等核心 NATS 操作。
     *
     * @param connectionManager NATS 连接管理器
     * @param serializer        消息序列化策略
     * @param headerInjector    header 注入策略
     * @param tracingSupport    追踪支撑策略
     * @param subscriberFactory 订阅工厂
     * @param errorStrategy     错误处理策略
     * @param properties        NATS 配置属性
     * @return Core NATS Bus 实例
     */
    @Bean
    @ConditionalOnMissingBean(NatsBus.class)
    public NatsBus natsBus(NatsConnectionManager connectionManager,
                           NatsMessageSerializer serializer,
                           NatsHeaderInjector headerInjector,
                           NatsTracingSupport tracingSupport,
                           NatsSubscriberFactory subscriberFactory,
                           NatsErrorStrategy errorStrategy,
                           NatsProperties properties) {
        return new NatsBus(connectionManager, serializer, headerInjector,
                tracingSupport, subscriberFactory, errorStrategy, properties);
    }

    /**
     * 注册 JetStream 消息总线 Bean。仅在 {@code platform.component.nats.jetstream.enabled=true}
     * 时启用；Bean 销毁时调用 {@code close()} 以排空内部 dispatcher。
     *
     * @param connectionManager NATS 连接管理器
     * @param serializer        消息序列化策略
     * @param headerInjector    header 注入策略
     * @param tracingSupport    追踪支撑策略
     * @param subscriberFactory 订阅工厂
     * @param errorStrategy     错误处理策略
     * @param properties        NATS 配置属性
     * @return JetStream Bus 实例
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(JetStreamBus.class)
    @ConditionalOnProperty(name = "platform.component.nats.jetstream.enabled", havingValue = "true")
    public JetStreamBus jetStreamBus(NatsConnectionManager connectionManager,
                                     NatsMessageSerializer serializer,
                                     NatsHeaderInjector headerInjector,
                                     NatsTracingSupport tracingSupport,
                                     NatsSubscriberFactory subscriberFactory,
                                     NatsErrorStrategy errorStrategy,
                                     NatsProperties properties) {
        return new JetStreamBus(connectionManager, serializer, headerInjector,
                tracingSupport, subscriberFactory, errorStrategy, properties);
    }

    /**
     * 当 JetStream 未启用时的 No-Op JetStreamBus（仅供 NatsComponent 构造用）。
     *
     * <p>为何需要该 Bean：{@link NatsComponent} 的构造器依赖 {@link JetStreamBus}，
     * 即使业务方禁用 JetStream 也必须存在一个 Bean，否则装配失败。</p>
     *
     * @param connectionManager NATS 连接管理器
     * @param serializer        消息序列化策略
     * @param headerInjector    header 注入策略
     * @param tracingSupport    追踪支撑策略
     * @param subscriberFactory 订阅工厂
     * @param errorStrategy     错误处理策略
     * @param properties        NATS 配置属性
     * @return 兜底的 JetStream Bus 实例
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(JetStreamBus.class)
    public JetStreamBus noopJetStreamBus(NatsConnectionManager connectionManager,
                                         NatsMessageSerializer serializer,
                                         NatsHeaderInjector headerInjector,
                                         NatsTracingSupport tracingSupport,
                                         NatsSubscriberFactory subscriberFactory,
                                         NatsErrorStrategy errorStrategy,
                                         NatsProperties properties) {
        return new JetStreamBus(connectionManager, serializer, headerInjector,
                tracingSupport, subscriberFactory, errorStrategy, properties);
    }

    /**
     * 注册 RPC 端点 Bean。负责服务端 handler 注册与请求分发，配合
     * {@link NatsBus#request} 完成 RPC 调用闭环。
     *
     * @param connectionManager NATS 连接管理器
     * @param serializer        消息序列化策略
     * @param headerInjector    header 注入策略
     * @param subscriberFactory 订阅工厂
     * @param errorStrategy     错误处理策略
     * @return RPC 端点实例
     */
    @Bean
    @ConditionalOnMissingBean(NatsEndpoint.class)
    public NatsEndpoint natsEndpoint(NatsConnectionManager connectionManager,
                                     NatsMessageSerializer serializer,
                                     NatsHeaderInjector headerInjector,
                                     NatsSubscriberFactory subscriberFactory,
                                     NatsErrorStrategy errorStrategy) {
        return new NatsEndpoint(connectionManager, serializer, headerInjector,
                subscriberFactory, errorStrategy);
    }

    // ==================== L6 统一门面 ====================

    /**
     * 注册统一门面 {@link NatsComponent} Bean。对外暴露 bus / stream / endpoint /
     * keyValue / objectStore 等子模块访问入口，业务代码应仅依赖此 Bean。
     *
     * @param properties                 NATS 配置属性
     * @param connectionManager          NATS 连接管理器
     * @param jetStreamManagementService JetStream 管理服务
     * @param natsBus                    Core NATS 总线
     * @param jetStreamBus               JetStream 总线
     * @param natsEndpoint               RPC 端点
     * @return {@link NatsComponent} 门面实例
     */
    @Bean
    @ConditionalOnMissingBean(NatsComponent.class)
    public NatsComponent natsComponent(NatsProperties properties,
                                       NatsConnectionManager connectionManager,
                                       JetStreamManagementService jetStreamManagementService,
                                       NatsBus natsBus,
                                       JetStreamBus jetStreamBus,
                                       NatsEndpoint natsEndpoint) {
        return new NatsComponent(properties, connectionManager,
                jetStreamManagementService, natsBus, jetStreamBus, natsEndpoint);
    }

    // ==================== L7 DLQ (JetStream advisory 范式) ====================

    /**
     * 注册 DLQ 发布器 Bean。仅在 {@code platform.component.nats.jetstream.dlq.enabled=true}
     * 时启用；将重试耗尽的消息重路由到 DLQ stream。
     *
     * <p>{@code destroyMethod = ""} 表示由 {@link NatsDeadLetterAdvisoryConsumer} 协同管理生命周期，
     * 避免双重 close 导致 NATS API 抛出异常。</p>
     *
     * @param connectionManager NATS 连接管理器
     * @param properties        NATS 配置属性
     * @return DLQ 发布器实例
     */
    @Bean(destroyMethod = "")
    @ConditionalOnProperty(name = "platform.component.nats.jetstream.dlq.enabled", havingValue = "true")
    public NatsDeadLetterPublisher natsDeadLetterPublisher(NatsConnectionManager connectionManager,
                                                           NatsProperties properties) {
        return new NatsDeadLetterPublisher(natsJetStream(connectionManager), properties);
    }

    /**
     * 注册 DLQ advisory 消费者 Bean。仅在 DLQ 启用时启用；订阅
     * {@code js.consumer.delivery.term.*} 系列主题，检测原 consumer 重试耗尽并触发重路由。
     * Bean 销毁时调用 {@code stop()} 退出订阅循环。
     *
     * @param connectionManager       NATS 连接管理器
     * @param natsDeadLetterPublisher DLQ 发布器
     * @param properties              NATS 配置属性
     * @return DLQ advisory 消费者实例
     */
    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(name = "platform.component.nats.jetstream.dlq.enabled", havingValue = "true")
    public NatsDeadLetterAdvisoryConsumer natsDeadLetterAdvisoryConsumer(NatsConnectionManager connectionManager,
                                                                         NatsDeadLetterPublisher natsDeadLetterPublisher,
                                                                         NatsProperties properties) {
        return new NatsDeadLetterAdvisoryConsumer(
                natsConnection(connectionManager),
                natsJetStream(connectionManager),
                natsJetStreamManagement(connectionManager),
                natsDeadLetterPublisher,
                properties
        );
    }

    // ==================== 内部方法 ====================

    /**
     * 构建 Redis 版幂等去重器，并在 {@code GlobalCache} 不在 classpath 时降级为内存实现。
     *
     * <p>为何捕获 {@link NoClassDefFoundError}：本组件不强依赖 cache 模块；当用户仅引入
     * {@code atlas-richie-nats} 而未引入 cache 时，{@code GlobalCache} 静态引用
     * 在类加载阶段即抛出 {@code NoClassDefFoundError}，因此必须兜底。</p>
     *
     * @param properties NATS 配置属性
     * @return Redis 或内存版幂等去重器
     */
    private NatsIdempotentChecker redisIdempotentChecker(NatsProperties properties) {
        try {
            return new RedisNatsIdempotentChecker();
        } catch (NoClassDefFoundError e) {
            // GlobalCache 不在 classpath 中，回退到内存实现
            return new MemoryNatsIdempotentChecker();
        }
    }

    /**
     * 从连接管理器中获取 jnats 原生 {@link Connection}。
     *
     * @param connectionManager NATS 连接管理器
     * @return 当前活跃的 jnats 连接
     */
    private Connection natsConnection(NatsConnectionManager connectionManager) {
        return connectionManager.getConnection();
    }

    /**
     * 从当前连接派生 {@link JetStream} 上下文。
     *
     * @param connectionManager NATS 连接管理器
     * @return 与该连接绑定的 JetStream 上下文
     * @throws IllegalStateException 派生失败（连接未就绪或被关闭）时抛出
     */
    private JetStream natsJetStream(NatsConnectionManager connectionManager) {
        try {
            return natsConnection(connectionManager).jetStream();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to acquire JetStream from connection", e);
        }
    }

    /**
     * 从当前连接派生 {@link JetStreamManagement} 上下文。
     *
     * @param connectionManager NATS 连接管理器
     * @return 与该连接绑定的 JetStreamManagement 上下文
     * @throws IllegalStateException 派生失败（连接未就绪或被关闭）时抛出
     */
    private JetStreamManagement natsJetStreamManagement(NatsConnectionManager connectionManager) {
        try {
            return natsConnection(connectionManager).jetStreamManagement();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to acquire JetStreamManagement from connection", e);
        }
    }
}
