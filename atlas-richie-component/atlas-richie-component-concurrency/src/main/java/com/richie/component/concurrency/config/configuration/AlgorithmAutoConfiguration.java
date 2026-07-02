package com.richie.component.concurrency.config.configuration;

import com.richie.component.concurrency.algorithm.CircuitBreaker;
import com.richie.component.concurrency.algorithm.RateLimiter;
import com.richie.component.concurrency.config.ConcurrencyProperties;
import com.richie.component.concurrency.config.properties.CircuitBreakerProperties;
import com.richie.component.concurrency.config.properties.PoolProperties;
import com.richie.component.concurrency.config.properties.RateLimiterProperties;
import com.richie.component.concurrency.threadpool.DynamicExecutor;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 并发算法自动装配 —— 按需提供 {@link RateLimiter}、{@link CircuitBreaker} 与
 * {@link DynamicExecutor} 多线程池的 Spring Bean 注册。
 *
 * <p>本类不依赖
 * {@code platform.concurrency.async.enabled} 总开关；每个 Bean 独立由各自子系统的
 * {@code enabled} 开关控制，用户按需启用。</p>
 *
 * <h3>装配清单</h3>
 * <table>
 *   <tr><th>Bean</th><th>配置前缀</th><th>启用开关</th><th>默认</th></tr>
 *   <tr>
 *     <td>{@link RateLimiter}</td>
 *     <td>{@code platform.concurrency.rate-limiter}</td>
 *     <td>{@code enabled}</td>
 *     <td>{@code false}</td>
 *   </tr>
 *   <tr>
 *     <td>{@link CircuitBreaker}</td>
 *     <td>{@code platform.concurrency.circuit-breaker}</td>
 *     <td>{@code enabled}</td>
 *     <td>{@code false}</td>
 *   </tr>
 *   <tr>
 *     <td>{@link DynamicExecutor}（多池）</td>
 *     <td>{@code platform.concurrency.thread-pools.&lt;name&gt;.*}</td>
 *     <td>存在即注册</td>
 *     <td>空 Map</td>
 *   </tr>
 * </table>
 *
 * @author richie696
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ConcurrencyProperties.class, RateLimiterProperties.class, CircuitBreakerProperties.class})
public class AlgorithmAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmAutoConfiguration.class);

    /**
     * 令牌桶限流器 Spring Bean。
     *
     * <p>默认不活跃，通过 {@code platform.concurrency.rate-limiter.enabled=true} 激活。
     * 容器关闭时自动调用 {@link RateLimiter#close()} 释放底层调度器线程。</p>
     *
     * @param properties 统一配置属性，用于读取 {@code rate-limiter.permits-per-second}
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
     * <p>默认不活跃，通过 {@code platform.concurrency.circuit-breaker.enabled=true} 激活。
     * 参数映射：{@code failure-rate-threshold}（{@code 0.0~1.0}）乘以 {@code 100}
     * 转为 {@link CircuitBreaker.Builder#failurePercent(int)}；{@code wait-duration}
     * 映射到 {@link CircuitBreaker.Builder#openDuration(java.time.Duration)}；
     * {@code sliding-window-size} 映射到 {@link CircuitBreaker.Builder#windowSize(int)}。</p>
     *
     * @param properties 统一配置属性，用于读取 {@code circuit-breaker.*} 参数
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

    // ========================================================================
    // 动态线程池 — 多池支持
    // ========================================================================

    /**
     * 动态线程池自动装配 —— 根据 {@code platform.concurrency.thread-pools} 配置
     * 为每个命名池注册独立的 {@link DynamicExecutor} Spring Bean。
     *
     * <p>每个池的名称即为 Bean 名称，业务方可通过以下方式注入：</p>
     * <pre>{@code
     * // 方式一：按名称获取指定线程池
     * @Resource(name = "order-executor")
     * private DynamicExecutor orderExecutor;
     *
     * // 方式二：注入全部线程池
     * @Autowired
     * private Map<String, DynamicExecutor> executors;
     *
     * // 方式三：@Qualifier + @Autowired
     * @Qualifier("notification-executor")
     * @Autowired
     * private DynamicExecutor notificationExecutor;
     * }</pre>
     *
     * <p>容器关闭时会自动 {@code shutdown()} 所有注册的线程池。</p>
     */
    @Configuration
    static class DynamicExecutorConfiguration {

        private static final Logger log = LoggerFactory.getLogger(DynamicExecutorConfiguration.class);

        private final List<DynamicExecutor> executors = new ArrayList<>();

        private final ConcurrencyProperties properties;
        private final ConfigurableListableBeanFactory beanFactory;

        DynamicExecutorConfiguration(ConcurrencyProperties properties, ConfigurableListableBeanFactory beanFactory) {
            this.properties = properties;
            this.beanFactory = beanFactory;
        }

        @PostConstruct
        void init() {
            Map<String, PoolProperties> pools = properties.getThreadPools();
            if (pools == null || pools.isEmpty()) {
                return;
            }
            pools.forEach((name, config) -> {
                String prefix = config.getThreadNamePrefix().isEmpty() ? name + "-" : config.getThreadNamePrefix();
                DynamicExecutor executor = new DynamicExecutor(
                        config.getCorePoolSize(), config.getMaximumPoolSize(),
                        config.getKeepAliveTime().toMillis(), TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<>(config.getQueueCapacity()),
                        new DynamicExecutorThreadFactory(prefix),
                        parseRejectedHandler(config.getRejectedHandler()));

                executors.add(executor);
                beanFactory.registerSingleton(name, executor);

                log.info("Concurrency dynamic thread pool [{}]: core={}, max={}, keepAlive={}, queue={}, handler={}",
                        name, config.getCorePoolSize(), config.getMaximumPoolSize(),
                        config.getKeepAliveTime(), config.getQueueCapacity(),
                        config.getRejectedHandler());
            });
        }

        @PreDestroy
        void shutdown() {
            executors.forEach(executor -> {
                try {
                    executor.shutdown();
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            });
        }

        private static RejectedExecutionHandler parseRejectedHandler(String name) {
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

    /**
     * 可命名线程工厂 —— 为线程池提供带前缀的自增命名线程。
     */
    private static class DynamicExecutorThreadFactory implements ThreadFactory {

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
}
