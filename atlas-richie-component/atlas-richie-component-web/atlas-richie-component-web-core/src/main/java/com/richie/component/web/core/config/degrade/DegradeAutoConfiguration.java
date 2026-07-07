package com.richie.component.web.core.config.degrade;

import com.richie.component.web.core.degrade.DegradeInterceptor;
import com.richie.component.web.core.degrade.DegradeStrategy;
import com.richie.component.web.core.degrade.DegradeStrategyRegistry;
import com.richie.component.web.core.degrade.DefaultDegradeStrategyRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 降级自动装配（README.md §4.7 / §5.2）。
 * <p>
 * 装配：
 * <ol>
 *   <li>{@link DegradeStrategyRegistry}：默认 {@link DefaultDegradeStrategyRegistry}</li>
 *   <li>{@link DegradeInterceptor}：可选 bean（仅在开关打开时）</li>
 * </ol>
 *
 * <h2>启用条件</h2>
 * <ul>
 *   <li>{@code platform.component.web.degrade.enabled=true}（默认）</li>
 *   <li>{@link DegradeStrategy} 类在 classpath（永远成立：定义在本模块）</li>
 * </ul>
 *
 * <h2>用户接入</h2>
 * <p>业务方定义 {@code @Component public class MyDegrade implements DegradeStrategy {...}} 即可被
 * 自动注册到 {@link DegradeStrategyRegistry}（本装配不强制 {@code @Bean} 显式声明）。
 *
 * @author richie696
 * @since 2026-07
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(DegradeStrategy.class)
@EnableConfigurationProperties(DegradeProperties.class)
public class DegradeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DegradeStrategyRegistry degradeStrategyRegistry() {
        log.info("DegradeAutoConfiguration: DefaultDegradeStrategyRegistry initialized");
        return new DefaultDegradeStrategyRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "platform.component.web.degrade", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public DegradeInterceptor degradeInterceptor(DegradeStrategyRegistry registry,
                                                 DegradeProperties properties,
                                                 BeanFactory beanFactory) {
        log.info("DegradeAutoConfiguration: DegradeInterceptor enabled (ORDER={})", DegradeInterceptor.ORDER);
        return new DegradeInterceptor(registry, properties,
                DegradeInterceptor.DEFAULT_LATENCY_THRESHOLD_MS, beanFactory);
    }
}