package com.richie.component.nats.config;

import com.richie.component.nats.NatsComponent;
import com.richie.component.nats.bus.NatsBus;
import com.richie.component.nats.connection.NatsConnectionManager;
import com.richie.component.nats.bus.NatsEndpoint;
import com.richie.component.nats.dlq.NatsDeadLetterAdvisoryConsumer;
import com.richie.component.nats.dlq.NatsDeadLetterPublisher;
import com.richie.component.nats.strategy.DefaultNatsErrorStrategy;
import com.richie.component.nats.strategy.DefaultNatsHeaderExtractor;
import com.richie.component.nats.strategy.DefaultNatsHeaderInjector;
import com.richie.component.nats.strategy.MemoryNatsIdempotentChecker;
import com.richie.component.nats.strategy.RedisNatsIdempotentChecker;
import com.richie.component.nats.strategy.JacksonNatsMessageSerializer;
import com.richie.component.nats.strategy.OpenTelemetryNatsTracingSupport;
import com.richie.component.nats.connection.JetStreamManagementService;
import com.richie.component.nats.pipeline.NatsSubscriberFactory;
import com.richie.component.nats.strategy.NatsErrorStrategy;
import com.richie.component.nats.strategy.NatsHeaderExtractor;
import com.richie.component.nats.strategy.NatsHeaderInjector;
import com.richie.component.nats.strategy.NatsIdempotentChecker;
import com.richie.component.nats.strategy.NatsMessageSerializer;
import com.richie.component.nats.strategy.NatsTracingSupport;
import com.richie.component.nats.bus.JetStreamBus;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * NATS 组件自动配置
 *
 * <p>根据 {@code platform.nats.*} 配置属性按需装配所有 Bean。
 * 策略接口均提供默认实现，用户可通过自定义 Bean 替换。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(NatsProperties.class)
@ConditionalOnProperty(name = "platform.nats.enabled", havingValue = "true", matchIfMissing = true)
public class NatsAutoConfiguration {

    // ==================== L1/L2 策略实现 ====================

    @Bean
    @ConditionalOnMissingBean(NatsMessageSerializer.class)
    public NatsMessageSerializer natsMessageSerializer() {
        return new JacksonNatsMessageSerializer();
    }

    @Bean
    @ConditionalOnMissingBean(NatsHeaderInjector.class)
    public NatsHeaderInjector natsHeaderInjector(NatsProperties properties) {
        return new DefaultNatsHeaderInjector(properties.getHeaderPropagation().getHeaders());
    }

    @Bean
    @ConditionalOnMissingBean(NatsHeaderExtractor.class)
    public NatsHeaderExtractor natsHeaderExtractor(NatsProperties properties) {
        return new DefaultNatsHeaderExtractor(properties.getHeaderPropagation().getHeaders());
    }

    @Bean
    @ConditionalOnMissingBean(NatsTracingSupport.class)
    @ConditionalOnClass(name = "io.opentelemetry.api.GlobalOpenTelemetry")
    @ConditionalOnProperty(name = "platform.nats.tracing.enabled", havingValue = "true", matchIfMissing = true)
    public NatsTracingSupport natsTracingSupport(NatsProperties properties) {
        return new OpenTelemetryNatsTracingSupport(true);
    }

    /**
     * 当 classpath 无 OpenTelemetry 时的 No-Op 追踪实现。
     * <p>本 Bean 仅在 OpenTelemetry 类存在且 {@code natsTracingSupport} 由于
     * {@code platform.nats.tracing.enabled=false} 未创建时兜底。OTel 缺失时直接
     * 不创建，让下游 bean 装配以更明确的 {@code NoSuchBeanDefinitionException} 失败，
     * 而不是抛出误导性的 {@code NoClassDefFoundError}。</p>
     */
    @Bean
    @ConditionalOnMissingBean(NatsTracingSupport.class)
    @ConditionalOnClass(name = "io.opentelemetry.api.GlobalOpenTelemetry")
    public NatsTracingSupport noopNatsTracingSupport() {
        return new OpenTelemetryNatsTracingSupport(false);
    }

    @Bean
    @ConditionalOnMissingBean(NatsIdempotentChecker.class)
    @ConditionalOnProperty(name = "platform.nats.idempotent.enabled", havingValue = "true")
    public NatsIdempotentChecker natsIdempotentChecker(NatsProperties properties) {
        return switch (properties.getIdempotent().getDatasource().toLowerCase()) {
            case "redis" -> redisIdempotentChecker(properties);
            default -> new MemoryNatsIdempotentChecker();
        };
    }

    @Bean
    @ConditionalOnMissingBean(NatsIdempotentChecker.class)
    @ConditionalOnProperty(name = "platform.nats.idempotent.enabled", havingValue = "false", matchIfMissing = true)
    public NatsIdempotentChecker noopNatsIdempotentChecker() {
        // 幂等去重未启用时返回内存实现（SubscriberFactory 根据 enabled 标志决定是否使用）
        return new MemoryNatsIdempotentChecker();
    }

    @Bean
    @ConditionalOnMissingBean(NatsErrorStrategy.class)
    public NatsErrorStrategy natsErrorStrategy() {
        return new DefaultNatsErrorStrategy();
    }

    // ==================== L3 基础设施 ====================

    @Bean
    @ConditionalOnMissingBean(NatsConnectionManager.class)
    public NatsConnectionManager natsConnectionManager(NatsProperties properties) {
        return new NatsConnectionManager(properties);
    }

    @Bean
    @ConditionalOnMissingBean(JetStreamManagementService.class)
    @ConditionalOnProperty(name = "platform.nats.jetstream.enabled", havingValue = "true")
    public JetStreamManagementService jetStreamManagementService(NatsConnectionManager connectionManager) {
        return new JetStreamManagementService(connectionManager);
    }

    /**
     * 当 JetStream 未启用时的 No-Op 管理服务（仅供 NatsComponent 构造用）
     */
    @Bean
    @ConditionalOnMissingBean(JetStreamManagementService.class)
    public JetStreamManagementService noopJetStreamManagementService(NatsConnectionManager connectionManager) {
        return new JetStreamManagementService(connectionManager);
    }

    // ==================== L4 管道 ====================

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

    @Bean
    @ConditionalOnMissingBean(JetStreamBus.class)
    @ConditionalOnProperty(name = "platform.nats.jetstream.enabled", havingValue = "true")
    public JetStreamBus jetStreamBus(NatsConnectionManager connectionManager,
                                      NatsMessageSerializer serializer,
                                      NatsHeaderInjector headerInjector,
                                      NatsTracingSupport tracingSupport,
                                      NatsSubscriberFactory subscriberFactory,
                                      NatsErrorStrategy errorStrategy) {
        return new JetStreamBus(connectionManager, serializer, headerInjector,
                tracingSupport, subscriberFactory, errorStrategy);
    }

    /**
     * 当 JetStream 未启用时的 No-Op JetStreamBus（仅供 NatsComponent 构造用）
     */
    @Bean
    @ConditionalOnMissingBean(JetStreamBus.class)
    public JetStreamBus noopJetStreamBus(NatsConnectionManager connectionManager,
                                          NatsMessageSerializer serializer,
                                          NatsHeaderInjector headerInjector,
                                          NatsTracingSupport tracingSupport,
                                          NatsSubscriberFactory subscriberFactory,
                                          NatsErrorStrategy errorStrategy) {
        return new JetStreamBus(connectionManager, serializer, headerInjector,
                tracingSupport, subscriberFactory, errorStrategy);
    }

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

    @Bean(destroyMethod = "")
    @ConditionalOnProperty(name = "platform.nats.dlq.enabled", havingValue = "true")
    public NatsDeadLetterPublisher natsDeadLetterPublisher(NatsConnectionManager connectionManager,
                                                            NatsProperties properties) {
        return new NatsDeadLetterPublisher(natsJetStream(connectionManager), properties);
    }

    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(name = "platform.nats.dlq.enabled", havingValue = "true")
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

    private NatsIdempotentChecker redisIdempotentChecker(NatsProperties properties) {
        try {
            return new RedisNatsIdempotentChecker();
        } catch (NoClassDefFoundError e) {
            // GlobalCache 不在 classpath 中，回退到内存实现
            return new MemoryNatsIdempotentChecker();
        }
    }

    private Connection natsConnection(NatsConnectionManager connectionManager) {
        return connectionManager.getConnection();
    }

    private JetStream natsJetStream(NatsConnectionManager connectionManager) {
        try {
            return natsConnection(connectionManager).jetStream();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to acquire JetStream from connection", e);
        }
    }

    private JetStreamManagement natsJetStreamManagement(NatsConnectionManager connectionManager) {
        try {
            return natsConnection(connectionManager).jetStreamManagement();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to acquire JetStreamManagement from connection", e);
        }
    }
}
