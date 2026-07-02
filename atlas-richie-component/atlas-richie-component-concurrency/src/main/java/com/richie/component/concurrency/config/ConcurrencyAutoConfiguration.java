package com.richie.component.concurrency.config;

import com.richie.component.concurrency.config.configuration.AlgorithmAutoConfiguration;
import com.richie.component.concurrency.config.properties.CircuitBreakerProperties;
import com.richie.component.concurrency.config.properties.RateLimiterProperties;
import com.richie.component.concurrency.threadpool.DynamicExecutor;
import com.richie.component.concurrency.threadpool.ThreadPoolConfigRefresher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.Map;

/**
 * 并发组件统一自动装配入口 —— 聚合限流器、熔断器与动态线程池的装配。
 *
 * <p>本类是 Spring Boot {@code AutoConfiguration.imports} 中注册的唯一入口，
 * 通过 {@link Import @Import} 引入子装配类：</p>
 * <ul>
 *   <li>{@link AlgorithmAutoConfiguration} — 令牌桶限流器、熔断器、动态线程池多池注册</li>
 * </ul>
 *
 * <p>Spring Boot 标准自动装配机制，无需任何自定义注解即可生效。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties({
        ConcurrencyProperties.class,
        RateLimiterProperties.class,
        CircuitBreakerProperties.class
})
@Import(AlgorithmAutoConfiguration.class)
public class ConcurrencyAutoConfiguration {

    /**
     * 线程池配置自动刷新器 —— 监听 Spring Cloud {@code EnvironmentChangeEvent}，
     * 自动比对并刷新受影响的 {@link DynamicExecutor} 线程池。
     *
     * <p>仅当 classpath 中存在 Spring Cloud Context（即
     * {@code org.springframework.cloud.context.environment.EnvironmentChangeEvent}）
     * 时此 Bean 才会创建。无需任何额外配置即可生效。</p>
     *
     * @param executors  全部已注册的 {@link DynamicExecutor} Bean，按 poolName 索引
     * @param binder     用于从最新 {@code Environment} 重新绑定配置的 {@link Binder}
     * @param properties 当前配置属性
     * @return 配置刷新器实例
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.cloud.context.environment.EnvironmentChangeEvent")
    public ThreadPoolConfigRefresher threadPoolConfigRefresher(
            Map<String, DynamicExecutor> executors,
            Binder binder,
            ConcurrencyProperties properties) {
        return new ThreadPoolConfigRefresher(executors, binder, properties);
    }
}
