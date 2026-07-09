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
package com.richie.component.redis.streammq.config.monitor;

import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import com.richie.component.redis.streammq.monitor.RedisStreamBacklogMonitor;
import com.richie.component.redis.streammq.monitor.RedisStreamEndpoint;
import com.richie.component.redis.streammq.monitor.RedisStreamHealthIndicator;
import com.richie.component.redis.streammq.monitor.RedisStreamMetrics;
import com.richie.component.redis.streammq.stream.RedisStreamReactor;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis Stream 监控自动配置
 *
 * <p>自动配置 Redis Stream 监控相关的组件，包括：
 * <ul>
 *   <li><strong>监控指标收集器</strong>：RedisStreamMetrics</li>
 *   <li><strong>健康检查指示器</strong>：RedisStreamHealthIndicator</li>
 *   <li><strong>管理端点</strong>：RedisStreamEndpoint</li>
 * </ul>
 *
 * <p><strong>配置属性：</strong>
 * <pre>{@code
 * # 启用 Redis Stream 监控
 * platform.cache.redis.stream.monitoring.enabled=true
 *
 * # 启用健康检查
 * platform.cache.redis.stream.monitoring.health-check.enabled=true
 *
 * # 启用指标收集
 * platform.cache.redis.stream.monitoring.metrics.enabled=true
 * }</pre>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-09-15 16:46:23
 */
@Slf4j
@Configuration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(
    prefix = "platform.cache.redis.stream.monitoring",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties({RedisStreamMonitoringProperties.class})
public class RedisStreamMonitoringAutoConfiguration {

    /**
     * 配置 Redis Stream 监控指标收集器
     *
     * @param meterRegistry MeterRegistry 实例
     * @param properties    Stream 监控配置
     * @return RedisStreamMetrics 实例
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "platform.cache.redis.stream.monitoring.metrics",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public RedisStreamMetrics redisStreamMetrics(MeterRegistry meterRegistry, RedisStreamMonitoringProperties properties) {
        log.info("配置 Redis Stream 监控指标收集器");
        return new RedisStreamMetrics(meterRegistry, properties);
    }

    /**
     * 配置 Redis Stream 健康检查指示器
     *
     * @param redisTemplate  MultiRedisTemplate 实例
     * @param metrics       RedisStreamMetrics 实例
     * @param reactor       Stream 拉取反应器
     * @param meterRegistry MeterRegistry 实例
     * @param properties    Stream 监控配置
     * @return RedisStreamHealthIndicator 实例
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "platform.cache.redis.stream.monitoring.health-check",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public RedisStreamHealthIndicator redisStreamHealthIndicator(
            MultiRedisTemplate<Object> redisTemplate,
            RedisStreamMetrics metrics,
            RedisStreamReactor reactor,
            MeterRegistry meterRegistry,
            RedisStreamMonitoringProperties properties) {
        log.info("配置 Redis Stream 健康检查指示器");
        return new RedisStreamHealthIndicator(redisTemplate, metrics, reactor, meterRegistry, properties);
    }

    /**
     * 配置 Redis Stream 管理端点
     *
     * @param redisTemplate   MultiRedisTemplate 实例
     * @param metrics         RedisStreamMetrics 实例
     * @param meterRegistry   MeterRegistry 实例
     * @param healthIndicator RedisStreamHealthIndicator 实例
     * @param properties      Stream 监控配置
     * @param backlogMonitor  积压监控器
     * @return RedisStreamEndpoint 实例
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "management.endpoint.redis-stream",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public RedisStreamEndpoint redisStreamEndpoint(
            MultiRedisTemplate<Object> redisTemplate,
            RedisStreamMetrics metrics,
            MeterRegistry meterRegistry,
            RedisStreamHealthIndicator healthIndicator,
            RedisStreamMonitoringProperties properties,
            RedisStreamBacklogMonitor backlogMonitor) {
        log.info("配置 Redis Stream 管理端点");
        return new RedisStreamEndpoint(redisTemplate, metrics, meterRegistry, healthIndicator, properties, backlogMonitor);
    }
}


