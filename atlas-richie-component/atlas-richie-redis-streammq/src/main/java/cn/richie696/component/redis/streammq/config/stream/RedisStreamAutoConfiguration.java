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
package cn.richie696.component.redis.streammq.config.stream;

import cn.richie696.component.cache.redis.bean.MultiRedisTemplate;
import cn.richie696.component.cache.redis.bean.MultiStringRedisTemplate;
import cn.richie696.component.cache.redis.perf.RedisPerfGuard;
import cn.richie696.component.redis.streammq.StreamMQ;
import cn.richie696.component.redis.streammq.config.monitor.RedisStreamMonitoringProperties;
import cn.richie696.component.redis.streammq.function.StreamFunction;
import cn.richie696.component.redis.streammq.manage.RedisStreamManager;
import cn.richie696.component.redis.streammq.monitor.RedisStreamMetrics;
import cn.richie696.component.redis.streammq.stream.RedisStreamCleanup;
import cn.richie696.component.redis.streammq.stream.RedisStreamConsumerValidator;
import cn.richie696.component.redis.streammq.stream.RedisStreamIdempotencyGuard;
import cn.richie696.component.redis.streammq.stream.RedisStreamReactor;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Redis Stream 核心自动配置。
 *
 * <p>创建 StreamMQ 的核心 Bean：{@link RedisStreamReactor}、{@link RedisStreamManager}、
 * {@link RedisStreamCleanup}，并初始化 {@link StreamMQ} 静态门面。
 *
 * <p>通过 {@code platform.cache.redis.stream.enabled=true}（默认开启）控制整个模块的开关。
 *
 * @author richie696
 * @since 2025-12-09
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties({
        RedisStreamProperties.class,
        RedisStreamProperties.CleanupConfig.class,
        RedisStreamIdempotencyProperties.class
})
@ConditionalOnProperty(prefix = "platform.cache.redis.stream", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisStreamAutoConfiguration {

    /**
     * Stream 指标收集器（核心基础设施，供 Reactor / Manager / 监控共用）。
     */
    @Bean
    public RedisStreamMetrics redisStreamMetrics(MeterRegistry meterRegistry,
                                                  RedisStreamMonitoringProperties properties) {
        log.info("配置 Redis Stream 监控指标收集器");
        return new RedisStreamMetrics(meterRegistry, properties);
    }

    /**
     * Stream 拉取反应器（长轮询 + 自适应调度 + 控制总线）。
     */
    @Bean
    public RedisStreamReactor redisStreamReactor(
            @Qualifier("jsonTemplate") MultiRedisTemplate<Object> redisTemplate,
            MultiStringRedisTemplate stringRedisTemplate,
            RedisStreamMetrics metrics) {
        return new RedisStreamReactor(redisTemplate, stringRedisTemplate, metrics);
    }

    /**
     * Stream 管理器：实现 {@link StreamFunction}，封装发布/确认/事件流。
     *
     * <p>该 Bean 同时作为 {@link StreamFunction} 的唯一来源，
     * 无需额外暴露 {@code StreamFunction} 类型 Bean（Spring 可按类型直接注入此 Bean）。
     */
    @Bean
    public RedisStreamManager redisStreamManager(
            @Qualifier("jsonTemplate") MultiRedisTemplate<Object> redisTemplate,
            MultiStringRedisTemplate stringRedisTemplate,
            OpenTelemetry openTelemetry,
            RedisStreamReactor reactor,
            RedisStreamMetrics metrics,
            RedisPerfGuard redisPerfGuard) {
        return new RedisStreamManager(redisTemplate, stringRedisTemplate, openTelemetry, reactor, metrics, redisPerfGuard);
    }

    /**
     * Stream 消息清理定时任务。
     */
    @Bean
    public RedisStreamCleanup redisStreamCleanup(RedisStreamProperties properties) {
        return new RedisStreamCleanup(properties);
    }

    /**
     * 初始化 {@link StreamMQ} 静态门面（仅在所有核心 Bean 创建完成后执行）。
     */
    @Bean
    public static Object streamMQInitializer(StreamFunction streamFunction) {
        StreamMQ.initialize(streamFunction);
        log.info("StreamMQ 静态门面已初始化");
        return new Object();
    }

    /**
     * Stream 幂等性守卫：消费侧防重放（内存快速去重 + Redis 兜底）。
     */
    @Bean
    public RedisStreamIdempotencyGuard redisStreamIdempotencyGuard(
            @Qualifier("jsonTemplate") MultiRedisTemplate<Object> redisTemplate,
            RedisStreamIdempotencyProperties properties) {
        log.info("配置 Redis Stream 幂等性守卫");
        return new RedisStreamIdempotencyGuard(redisTemplate, properties);
    }

    /**
     * 校验 {@link cn.richie696.component.redis.streammq.stream.RedisStreamConsumer} 元注解使用正确：
     * 被注解的类必须继承 {@link cn.richie696.component.redis.streammq.stream.AbstractStreamConsumer}。
     *
     * <p>声明为 {@code static @Bean} 以确保 Spring 在早期阶段实例化它，
     * 能在其他消费者 Bean 创建后立即生效。
     */
    @Bean
    public static RedisStreamConsumerValidator redisStreamConsumerValidator() {
        return new RedisStreamConsumerValidator();
    }
}
