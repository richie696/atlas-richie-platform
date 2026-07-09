/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.web.core.config.sse;

import com.richie.component.web.core.metrics.WebMetrics;
import com.richie.component.web.core.sse.SseManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * SSE 长连接装配（README.md §4.4）。
 * <p>
 * 仅在 Servlet Web 应用下生效（{@link ConditionalOnWebApplication}），{@link SseManager}
 * 需 servlet 容器支持 {@link org.springframework.web.servlet.mvc.method.annotation.SseEmitter}。
 *
 * @author richie696
 * @since 2026-07
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(SseManager.class)
public class SseAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public SseManager sseManager(WebMetrics webMetrics) {
        return new SseManager(webMetrics);
    }
}