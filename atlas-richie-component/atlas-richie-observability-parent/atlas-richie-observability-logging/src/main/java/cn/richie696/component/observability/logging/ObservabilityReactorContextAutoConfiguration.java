/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.logging;

import cn.richie696.component.observability.core.ObservabilityState;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import io.micrometer.context.integration.Slf4jThreadLocalAccessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import reactor.core.publisher.Hooks;

/**
 * 开启 Reactor 的官方 ThreadLocal context-propagation 支持。
 */
@AutoConfiguration
@ConditionalOnClass({ContextRegistry.class, ThreadLocalAccessor.class, Hooks.class})
@ConditionalOnBean(ObservabilityState.class)
public class ObservabilityReactorContextAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SmartInitializingSingleton observabilityReactorContextInitializer(
            ObservabilityState state) {
        return new ObservabilityReactorContextInitializer(state);
    }

    private static final class ObservabilityReactorContextInitializer
            implements SmartInitializingSingleton {

        private static final String MDC_ACCESSOR_KEY = "cp.slf4j";
        private final ObservabilityState state;

        private ObservabilityReactorContextInitializer(ObservabilityState state) {
            this.state = state;
        }

        @Override
        public void afterSingletonsInstantiated() {
            if (state.disabled()) {
                return;
            }
            ContextRegistry registry = ContextRegistry.getInstance();
            if (registry.getThreadLocalAccessors().stream()
                    .noneMatch(accessor -> MDC_ACCESSOR_KEY.equals(accessor.key()))) {
                registry.registerThreadLocalAccessor(new Slf4jThreadLocalAccessor(
                        "trace_id", "span_id", "request_id", "operation", "stage",
                        "status", "duration_ms", "error.type", "error.stage"));
            }
            Hooks.enableAutomaticContextPropagation();
        }
    }
}
