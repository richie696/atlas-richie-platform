package com.richie.component.concurrency.config.properties;

import com.richie.component.concurrency.config.ConcurrencyAutoConfiguration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 令牌桶限流器子系统配置 —— 绑定 {@code platform.concurrency.rate-limiter.*} 命名空间。
 *
 * <p>管理本组件提供的 {@link com.richie.component.concurrency.concurrent.RateLimiter} 令牌桶
 * 限流器的注册开关与默认速率。配置示例：</p>
 *
 * <pre>{@code
 * platform:
 *   concurrency:
 *     rate-limiter:
 *       enabled: true
 *       permits-per-second: 200
 * }</pre>
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li>{@link #enabled} —— 是否在 Spring 容器中注册令牌桶限流器 Bean（默认：{@code false}），
 *       默认不活跃，需要业务方显式开启</li>
 *   <li>{@link #permitsPerSecond} —— 每秒补充令牌数（默认：{@code 100}），同时也是令牌桶容量</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "platform.concurrency.rate-limiter")
public class RateLimiterProperties {

    /**
     * 是否在 Spring 容器中注册令牌桶限流器 Bean（默认：{@code false}）。
     *
     * <p>设为 {@code true} 后，{@link ConcurrencyAutoConfiguration}
     * 会创建一个 {@link com.richie.component.concurrency.concurrent.RateLimiter} Bean，
     * 容器关闭时自动调用 {@code close()} 释放底层调度器线程。</p>
     */
    private boolean enabled = false;

    /**
     * 每秒补充令牌数（默认：{@code 100}），同时也是令牌桶容量。
     *
     * <p>该值越大，单位时间内允许通过的请求越多；适用于"对下游外部 API 进行全局限流"的场景。</p>
     */
    private int permitsPerSecond = 100;
}
