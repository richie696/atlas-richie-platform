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
package com.richie.component.web.core.business;

import com.richie.component.web.core.config.business.BusinessIntegrationProperties;
import com.richie.component.web.core.spi.WebInterceptor;
import com.richie.component.web.core.spi.WebInterceptorChain;
import com.richie.component.web.core.spi.WebRequestContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;

/**
 * 业务能力集成（§4.9）：多租户解析。
 * <p>
 * 从配置 header（默认 {@code X-Tenant-Id}）解析租户 ID，写入：
 * <ul>
 *   <li>{@link WebRequestContext#setAttribute(String, Object) ctx.attribute}（key = {@link #TENANT_ATTRIBUTE}）</li>
 *   <li>SLF4J {@link MDC}（key = {@code "tenantId"}）便于日志关联</li>
 * </ul>
 *
 * <h2>Order</h2>
 * <p>{@link #ORDER} = 260：在 KeyResolver(250) 之后；RateLimit/CB 之前。
 *
 * <h2>无 gateway 互斥</h2>
 * <p>与 §4.8 防护层不同，本拦截器<strong>不</strong>读 {@code X-Forwarded-From-Gateway}，
 * 即使 web 端与 gateway 同部署也会双写双解析（最后写入者赢，README.md §4.9 R11）。
 *
 * @author richie696
 * @since 2026-07
 */
@Slf4j
public class TenantInterceptor implements WebInterceptor, Ordered {

    /** ctx.attribute key。 */
    public static final String TENANT_ATTRIBUTE = "platform.web.tenant";

    /** SLF4J MDC key。 */
    public static final String MDC_KEY = "tenantId";

    /** 拦截器在链中的位置。 */
    public static final int ORDER = 260;

    private final String headerName;
    private final boolean requireOnMissing;

    public TenantInterceptor(BusinessIntegrationProperties.Tenant config) {
        this.headerName = config.getHeaderName();
        this.requireOnMissing = config.isRequireOnMissing();
    }

    @Override
    public void intercept(WebRequestContext ctx, WebInterceptorChain chain) throws Exception {
        String tenant = ctx.header(headerName);
        if (tenant == null || tenant.isBlank()) {
            if (requireOnMissing) {
                ctx.markShortCircuit(400,
                        "{\"error\":\"missing_tenant\",\"reason\":\"tenant_header_required\"}");
                return;
            }
            // 缺 tenant 不阻断：可能是不需要租户的内部端点
            chain.proceed(ctx);
            return;
        }
        String trimmed = tenant.trim();
        ctx.setAttribute(TENANT_ATTRIBUTE, trimmed);
        try {
            MDC.put(MDC_KEY, trimmed);
        } catch (RuntimeException mdcEx) {
            // MDC 在非 SLF4J 绑定（如单元测试）可能 NPE；降级不阻断
            log.debug("MDC.put failed: {}", mdcEx.getMessage());
        }
        try {
            chain.proceed(ctx);
        } finally {
            // 清理 MDC 防止线程复用污染
            try {
                MDC.remove(MDC_KEY);
            } catch (RuntimeException ignored) {
                // best-effort cleanup
            }
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}