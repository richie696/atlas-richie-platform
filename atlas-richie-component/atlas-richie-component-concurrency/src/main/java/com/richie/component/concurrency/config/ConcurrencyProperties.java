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
package com.richie.component.concurrency.config;

import com.richie.component.concurrency.config.properties.CircuitBreakerProperties;
import com.richie.component.concurrency.config.properties.PoolProperties;
import com.richie.component.concurrency.config.properties.RateLimiterProperties;
import com.richie.component.concurrency.threadpool.DynamicExecutor;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 并发组件统一配置属性 —— 集中管理令牌桶限流、熔断器、动态线程池三大子系统的全部开关与参数。
 *
 * <p>本类是 {@code @ConfigurationProperties} 的统一入口，统一前缀 {@code platform.concurrency}，
 * 通过三个独立 Properties 类加上行内配置拆分子模块的命名空间：</p>
 *
 * <ul>
 *   <li>{@link RateLimiterProperties} —— 绑定 {@code platform.concurrency.rate-limiter.*}</li>
 *   <li>{@link CircuitBreakerProperties} —— 绑定 {@code platform.concurrency.circuit-breaker.*}</li>
 *   <li>{@code threadPools} —— 绑定 {@code platform.concurrency.thread-pools.*}，管理多个命名
 *       动态线程池的参数。每个 Key 即为线程池名称（同时也是 Spring Bean 名称），
 *       有配置就创建，不配置就没有</li>
 * </ul>
 *
 * <p>配置示例：</p>
 * <pre>{@code
 * platform:
 *   concurrency:
 *     rate-limiter:
 *       enabled: true
 *       permits-per-second: 200
 *     circuit-breaker:
 *       enabled: true
 *       failure-rate-threshold: 0.6
 *       sliding-window-size: 100
 *       wait-duration: 15s
 *     thread-pools:
 *       order-executor:
 *         core-pool-size: 8
 *         maximum-pool-size: 16
 *         keep-alive-time: 30s
 *         queue-capacity: 500
 *         rejected-handler: CallerRunsPolicy
 *       notification-executor:
 *         core-pool-size: 2
 *         maximum-pool-size: 4
 *         keep-alive-time: 60s
 *         queue-capacity: 200
 * }</pre>
 *
 * @author richie696
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "platform.concurrency")
@Data
public class ConcurrencyProperties {

    /**
     * 限流器子系统配置 —— {@code platform.concurrency.rate-limiter.*}。
     */
    private RateLimiterProperties rateLimiter = new RateLimiterProperties();

    /**
     * 熔断器子系统配置 —— {@code platform.concurrency.circuit-breaker.*}。
     */
    private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();

    /**
     * 多命名线程池配置 —— {@code platform.concurrency.thread-pools.*}。
     *
     * <p>Key 为线程池名称（同时也是 Spring Bean 名称），Value 为具体参数。
     * 有配置就创建对应 {@link DynamicExecutor} Bean，
     * 不配置就没有。每个 Key 可作为 {@code @Qualifier} 的引用值。</p>
     *
     * <p>Spring Boot 4.x 在 {@code @ConfigurationProperties} 嵌套
     * {@code Map<String, POJO>} 场景下不会自动识别 value 类型,
     * {@link PoolProperties} 上已显式标注 {@code @ConfigurationProperties}
     * 修复该问题。</p>
     */
    private Map<String, PoolProperties> threadPools = new LinkedHashMap<>();

}
