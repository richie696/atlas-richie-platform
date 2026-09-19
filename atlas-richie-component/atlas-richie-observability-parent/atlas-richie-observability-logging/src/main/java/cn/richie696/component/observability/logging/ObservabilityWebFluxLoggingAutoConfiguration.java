/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.logging;

import cn.richie696.component.observability.core.ObservabilityState;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.server.WebFilter;

/**
 * WebFlux 请求关联自动配置。
 *
 * <p>WebFlux 依赖是可选的。Servlet 应用不会因为完整 Starter 而被强制切换到响应式
 * Web 栈；只有应用自身存在 WebFlux 时才注册该过滤器。</p>
 */
@AutoConfiguration
@ConditionalOnClass(WebFilter.class)
@ConditionalOnBean(ObservabilityState.class)
public class ObservabilityWebFluxLoggingAutoConfiguration {

    @Bean(name = "atlasRichieObservabilityWebFluxFilter")
    @ConditionalOnMissingBean(name = "atlasRichieObservabilityWebFluxFilter")
    public WebFilter observabilityWebFluxFilter(ObservabilityState state) {
        return new ObservabilityWebFluxFilter(state);
    }
}
