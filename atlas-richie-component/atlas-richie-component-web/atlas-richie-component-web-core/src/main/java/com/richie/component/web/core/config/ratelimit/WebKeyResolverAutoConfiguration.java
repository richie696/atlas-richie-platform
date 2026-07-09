package com.richie.component.web.core.config.ratelimit;

import com.richie.component.web.core.spi.KeyResolver;
import com.richie.component.web.core.spi.support.HeaderBasedKeyResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * KeyResolver 默认装配（README.md §4.1）。
 * <p>
 * {@link com.richie.component.web.core.interceptor.RateLimitInterceptor} /
 * {@link com.richie.component.web.core.interceptor.CircuitBreakerInterceptor} 都依赖
 * {@link KeyResolver} bean 解析 clientKey；用户未显式提供时，本类按 {@code WebFilterProperties#keyResolverHeader}
 * 配置（默认 {@code X-Client-Id}）注册 {@link HeaderBasedKeyResolver}。
 *
 * <h2>配置驱动（铁律）</h2>
 * <p>用户可通过以下任一方式覆盖默认 KeyResolver：
 * <ul>
 *   <li>显式声明 {@code @Bean KeyResolver custom(...)}（{@link ConditionalOnMissingBean} 跳过本类）</li>
 *   <li>设置 {@code platform.component.web.key-resolver.enabled=false}（关闭默认）—— 若此时
 *       RateLimit / CB 的 {@code enabled=true} 且无自定义 KeyResolver，二者创建会因
 *       {@code @ConditionalOnBean(KeyResolver.class)} 失败而 bean 不存在（拦截器不生效），
 *       启动日志会有 warning</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(KeyResolver.class)
public class WebKeyResolverAutoConfiguration {

    /**
     * 注册 {@link HeaderBasedKeyResolver}，header 名取自 {@link WebFilterProperties#getKeyHeader()}
     * （默认 {@code X-Client-Id}）。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "platform.component.web.key-resolver", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public KeyResolver keyResolver(WebFilterProperties properties) {
        String header = properties.getKeyHeader();
        log.info("WebKeyResolverAutoConfiguration: registering default KeyResolver (header={})", header);
        return new HeaderBasedKeyResolver(header);
    }
}