/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.desensitize.logging.config;

import ch.qos.logback.classic.LoggerContext;
import com.richie.component.desensitize.logging.logback.SensitiveMdcTurboFilter;
import com.richie.component.desensitize.logging.logback.SensitiveLogArgTurboFilter;
import com.richie.component.desensitize.logging.service.DefaultLoggingMaskingService;
import com.richie.component.desensitize.logging.service.LoggingMaskingService;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 日志脱敏自动配置。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
@AutoConfiguration(after = com.richie.component.desensitize.core.config.DesensitizeAutoConfiguration.class)
@ConditionalOnBean(com.richie.component.desensitize.core.service.MaskingService.class)
@ConditionalOnClass(LoggerContext.class)
public class LoggingDesensitizeAutoConfiguration {

    /**
     * loggingMaskingService。
     * @return 处理结果
     */
    @Bean
    @ConditionalOnMissingBean
    public LoggingMaskingService loggingMaskingService() {
        return new DefaultLoggingMaskingService();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "platform.component.desensitize.log.features",
            name = "sensitive-log-arg-turbo-filter-enabled",
            havingValue = "true",
            matchIfMissing = true)
    /**
     * sensitiveLogArgTurboFilter。
     * @return 处理结果
     */
    public SensitiveLogArgTurboFilter sensitiveLogArgTurboFilter() {
        return new SensitiveLogArgTurboFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "platform.component.desensitize.log.features",
            name = "sensitive-mdc-turbo-filter-enabled",
            havingValue = "true",
            matchIfMissing = true)
    /**
     * sensitiveMdcTurboFilter。
     * @param loggingMaskingService 参数
     * @return 处理结果
     */
    public SensitiveMdcTurboFilter sensitiveMdcTurboFilter(LoggingMaskingService loggingMaskingService) {
        return new SensitiveMdcTurboFilter(loggingMaskingService);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "platform.component.desensitize.log.features",
            name = "auto-register-turbo-filters",
            havingValue = "true",
            matchIfMissing = true)
    public SmartInitializingSingleton loggingTurboFilterRegistrar(
            ObjectProvider<SensitiveLogArgTurboFilter> argTurboFilterProvider,
            ObjectProvider<SensitiveMdcTurboFilter> mdcTurboFilterProvider) {
        return () -> {
            if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext loggerContext)) {
                return;
            }
            SensitiveLogArgTurboFilter argTurboFilter = argTurboFilterProvider.getIfAvailable();
            if (argTurboFilter != null && !loggerContext.getTurboFilterList().contains(argTurboFilter)) {
                argTurboFilter.start();
                loggerContext.addTurboFilter(argTurboFilter);
            }
            SensitiveMdcTurboFilter mdcTurboFilter = mdcTurboFilterProvider.getIfAvailable();
            if (mdcTurboFilter != null && !loggerContext.getTurboFilterList().contains(mdcTurboFilter)) {
                mdcTurboFilter.start();
                loggerContext.addTurboFilter(mdcTurboFilter);
            }
        };
    }
}

