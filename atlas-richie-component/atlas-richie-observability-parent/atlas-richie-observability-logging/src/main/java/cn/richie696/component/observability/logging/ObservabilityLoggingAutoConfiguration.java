/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.logging;

import cn.richie696.component.observability.core.ObservabilityState;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet 日志关联自动配置。
 */
@AutoConfiguration
@ConditionalOnClass({Filter.class, OncePerRequestFilter.class})
@ConditionalOnBean(ObservabilityState.class)
public class ObservabilityLoggingAutoConfiguration {

    @Bean
    public FilterRegistrationBean<ObservabilityMdcFilter> observabilityMdcFilter(
            ObservabilityState state) {
        FilterRegistrationBean<ObservabilityMdcFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ObservabilityMdcFilter(state));
        registration.setName("atlasRichieObservabilityMdcFilter");
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        return registration;
    }
}
