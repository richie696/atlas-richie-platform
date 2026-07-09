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
package com.richie.component.concurrency.config.configuration;

import com.richie.component.concurrency.algorithm.CircuitBreaker;
import com.richie.component.concurrency.algorithm.RateLimiter;
import com.richie.component.concurrency.config.ConcurrencyProperties;
import com.richie.component.concurrency.config.properties.CircuitBreakerProperties;
import com.richie.component.concurrency.config.properties.RateLimiterProperties;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 并发算法自动装配 —— 按需提供 {@link RateLimiter}、{@link CircuitBreaker} Spring Bean。
 *
 * <p>动态线程池装配由 {@code DynamicExecutorRegistrar} ({@link org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor})
 * 独立处理,避免 Spring Boot 4.x 嵌套 Map {@code @ConfigurationProperties} 绑定兼容性问题,
 * 同时规避 Spring 7 对 {@code @AutoConfiguration} 类嵌套 {@code @Configuration} 子类的加载限制。</p>
 *
 * <p>本类不依赖 {@code platform.concurrency.async.enabled} 总开关;每个 Bean 独立由各自子系统的
 * {@code enabled} 开关控制,用户按需启用。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties({ConcurrencyProperties.class, RateLimiterProperties.class, CircuitBreakerProperties.class})
public class AlgorithmAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmAutoConfiguration.class);

    /**
     * 令牌桶限流器 Spring Bean。
     *
     * <p>默认不活跃,通过 {@code platform.concurrency.rate-limiter.enabled=true} 激活。
     * 容器关闭时自动调用 {@link RateLimiter#close()} 释放底层调度器线程。</p>
     *
     * @param properties 统一配置属性,用于读取 {@code rate-limiter.permits-per-second}
     * @return 令牌桶限流器实例
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "platform.concurrency.rate-limiter", name = "enabled", havingValue = "true", matchIfMissing = false)
    public RateLimiter rateLimiter(ConcurrencyProperties properties) {
        int permitsPerSecond = properties.getRateLimiter().getPermitsPerSecond();
        log.info("Concurrency rate limiter: registered with {} tokens/second, destroyMethod=close", permitsPerSecond);
        return RateLimiter.ofTokensPerSecond(permitsPerSecond);
    }

    /**
     * 熔断器 Spring Bean。
     *
     * <p>默认不活跃,通过 {@code platform.concurrency.circuit-breaker.enabled=true} 激活。
     * 参数映射:{@code failure-rate-threshold}({@code 0.0~1.0})乘以 {@code 100}
     * 转为 {@link CircuitBreaker.Builder#failurePercent(int)};{@code wait-duration}
     * 映射到 {@link CircuitBreaker.Builder#openDuration(java.time.Duration)};
     * {@code sliding-window-size} 映射到 {@link CircuitBreaker.Builder#windowSize(int)}。</p>
     *
     * @param properties 统一配置属性,用于读取 {@code circuit-breaker.*} 参数
     * @return 配置完成的 {@link CircuitBreaker} 实例
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.concurrency.circuit-breaker", name = "enabled", havingValue = "true", matchIfMissing = false)
    public CircuitBreaker circuitBreaker(ConcurrencyProperties properties) {
        CircuitBreakerProperties cb = properties.getCircuitBreaker();
        int failurePercent = (int) Math.round(cb.getFailureRateThreshold() * 100.0);
        if (cb.getHalfOpenMaxSuccesses() > 1) {
            log.debug("Concurrency circuit breaker: half-open-max-successes={} is reserved for future use (current builder uses single-probe semantics)",
                    cb.getHalfOpenMaxSuccesses());
        }
        log.info("Concurrency circuit breaker: registered with failurePercent={}%, slidingWindowSize={}, waitDuration={}",
                failurePercent, cb.getSlidingWindowSize(), cb.getWaitDuration());
        return CircuitBreaker.builder()
                .failurePercent(failurePercent)
                .windowSize(cb.getSlidingWindowSize())
                .openDuration(cb.getWaitDuration())
                .build();
    }

    /**
     * 可命名线程工厂 —— 为线程池提供带前缀的自增命名线程。
     *
     * <p>包级访问权限,供 {@code DynamicExecutorRegistrar} 复用。</p>
     */
    static final class DynamicExecutorThreadFactory implements ThreadFactory {

        private final String namePrefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        DynamicExecutorThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(@Nonnull Runnable r) {
            Thread t = new Thread(r);
            t.setName(namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(false);
            return t;
        }
    }

    /**
     * 解析字符串形式的拒绝策略名称,返回标准 JDK {@link RejectedExecutionHandler}。
     *
     * <p>包级访问权限,供 {@code DynamicExecutorRegistrar} 复用。</p>
     *
     * @param name 大小写不敏感的策略名({@code AbortPolicy} / {@code CallerRunsPolicy} /
     *             {@code DiscardPolicy} / {@code DiscardOldestPolicy})
     * @return 对应的 JDK 内置策略实例
     * @throws IllegalArgumentException 当 {@code name} 不在白名单中
     */
    static RejectedExecutionHandler parseRejectedHandler(String name) {
        return switch (name.trim().toLowerCase()) {
            case "abortpolicy" -> new ThreadPoolExecutor.AbortPolicy();
            case "callerrunspolicy" -> new ThreadPoolExecutor.CallerRunsPolicy();
            case "discardpolicy" -> new ThreadPoolExecutor.DiscardPolicy();
            case "discardoldestpolicy" -> new ThreadPoolExecutor.DiscardOldestPolicy();
            default -> throw new IllegalArgumentException(
                    "Unsupported rejected handler: " + name + ". Supported: AbortPolicy, CallerRunsPolicy, DiscardPolicy, DiscardOldestPolicy");
        };
    }
}