package com.richie.component.web.core.config.tracing;

import com.richie.component.web.core.tracing.OtelTracingInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 追踪拦截器自动装配（README.md §4.3 / §6 R3）。
 *
 * @author richie696
 * @since 2026-07
 */
@AutoConfiguration
@ConditionalOnClass(OtelTracingInterceptor.class)
public class TracingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OtelTracingInterceptor otelTracingInterceptor() {
        return new OtelTracingInterceptor();
    }
}