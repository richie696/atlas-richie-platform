/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.web.core.business;

import com.richie.component.web.core.config.business.BusinessIntegrationProperties;
import com.richie.component.web.core.spi.WebInterceptor;
import com.richie.component.web.core.spi.WebInterceptorChain;
import com.richie.component.web.core.spi.WebRequestContext;
import org.springframework.core.Ordered;

/**
 * 业务能力集成（§4.9）：API 版本协商。
 * <p>
 * 从配置 header（默认 {@code X-Api-Version}）读取 API 版本，写入 {@link WebRequestContext#setAttribute}，
 * 供 controller 路由或 controller 内部根据版本走不同代码分支。
 *
 * <h2>缺失 header 行为</h2>
 * <p>使用 {@code defaultVersion}（默认 {@code "default"}）——不阻断。
 *
 * <h2>与 Canary 的区别</h2>
 * <p>Canary（{@code atlas-richie-gateway-service} CanaryIdExtractorFilter）是<strong>流量切分</strong>（灰度路由），
 * 与本拦截器的<strong>代码版本</strong>语义不同（见 README.md §4.9）。web 端不做 Canary。
 *
 * <h2>Order</h2>
 * <p>{@link #ORDER} = 280：所有业务集成拦截器最后跑；RateLimit/CB 之前。
 *
 * @author richie696
 * @since 2026-07
 */
public class ApiVersionInterceptor implements WebInterceptor, Ordered {

    /** 拦截器在链中的位置。 */
    public static final int ORDER = 280;

    private final String headerName;
    private final String defaultVersion;
    private final String attributeKey;

    public ApiVersionInterceptor(BusinessIntegrationProperties.ApiVersion config) {
        this.headerName = config.getHeaderName();
        this.defaultVersion = config.getDefaultVersion();
        this.attributeKey = config.getAttributeKey();
    }

    @Override
    public void intercept(WebRequestContext ctx, WebInterceptorChain chain) throws Exception {
        String version = ctx.header(headerName);
        if (version == null || version.isBlank()) {
            ctx.setAttribute(attributeKey, defaultVersion);
        } else {
            ctx.setAttribute(attributeKey, version.trim());
        }
        chain.proceed(ctx);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}