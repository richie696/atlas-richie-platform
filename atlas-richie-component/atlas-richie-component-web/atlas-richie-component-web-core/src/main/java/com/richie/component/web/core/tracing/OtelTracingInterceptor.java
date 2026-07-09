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
package com.richie.component.web.core.tracing;

import com.richie.component.web.core.spi.WebInterceptor;
import com.richie.component.web.core.spi.WebInterceptorChain;
import com.richie.component.web.core.spi.WebRequestContext;
import org.springframework.core.Ordered;

/**
 * OTel 透传拦截器（README.md §4.3 / §6 R3）。
 * <p>
 * 拦截器链<strong>最早期</strong>（{@link #ORDER} = 50），先于 §4.8 防护 A 组
 * 让 trace ID 尽早写入 ctx，便于后续拦截器写入响应头。
 *
 * <h2>行为</h2>
 * <ol>
 *   <li>解析上游 {@code traceparent}（W3C 标准）</li>
 *   <li>若无则读 {@code X-Request-Id}（业务方自定义）</li>
 *   <li>若都无则自生成 32-hex UUID</li>
 *   <li>写入 ctx.traceId() + 响应头 {@code X-Trace-Id}</li>
 * </ol>
 *
 * <h2>OTel SDK</h2>
 * <p>本类<strong>不</strong>依赖 OTel SDK。{@code R3} 倾向 optional——SDK 是否启用由业务方决定。
 *
 * @author richie696
 * @since 2026-07
 */
public class OtelTracingInterceptor implements WebInterceptor, Ordered {

    /** Order=50：早于 PlatformProtection(100)，让 traceId 第一时间入 ctx。 */
    public static final int ORDER = 50;

    @Override
    public void intercept(WebRequestContext ctx, WebInterceptorChain chain) throws Exception {
        String traceId = TraceIdParser.resolve(
                ctx.header(TraceIdParser.TRACEPARENT_HEADER),
                ctx.header(TraceIdParser.X_REQUEST_ID_HEADER));
        ctx.setTraceId(traceId);
        ctx.addResponseHeader(TraceIdParser.RESPONSE_TRACE_ID_HEADER, traceId);
        chain.proceed(ctx);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}