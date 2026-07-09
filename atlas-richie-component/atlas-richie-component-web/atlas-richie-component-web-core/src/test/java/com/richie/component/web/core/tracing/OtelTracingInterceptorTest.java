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

import com.richie.component.web.core.spi.support.DefaultWebInterceptorChain;
import com.richie.component.web.core.spi.support.MutableWebRequestContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OtelTracingInterceptorTest {

    private final OtelTracingInterceptor interceptor = new OtelTracingInterceptor();

    @Test
    void traceparent_setsTraceId() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
                .build();
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat(ctx.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat((String) ctx.responseHeaders().get(TraceIdParser.RESPONSE_TRACE_ID_HEADER))
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    void requestIdHeader_setsTraceId() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header("X-Request-Id", "client-trace-abc")
                .build();
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat(ctx.traceId()).isNotNull();
        assertThat(ctx.traceId()).hasSize(32);
        assertThat(ctx.traceId()).startsWith("client-trace-abc");
    }

    @Test
    void noHeaders_generatesRandom() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .build();
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat(ctx.traceId()).hasSize(32).matches("[0-9a-f]{32}");
        assertThat((String) ctx.responseHeaders().get(TraceIdParser.RESPONSE_TRACE_ID_HEADER)).isNotNull();
    }

    @Test
    void callsChainProceed() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .build();
        boolean[] proceeded = {false};
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of((c, ch) -> proceeded[0] = true)));
        assertThat(proceeded[0]).isTrue();
    }

    @Test
    void getOrder_is50() {
        assertThat(interceptor.getOrder()).isEqualTo(50);
    }

    @Test
    void responseHeader_addedEvenWhenChainNotCalled() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .build();
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        Map<String, String> headers = ctx.responseHeaders();
        assertThat(headers).containsKey(TraceIdParser.RESPONSE_TRACE_ID_HEADER);
    }
}