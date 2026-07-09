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
package com.richie.component.web.core.protection;

import com.richie.component.web.core.config.protection.PlatformProtectionProperties;
import com.richie.component.web.core.spi.WebInterceptorChain;
import com.richie.component.web.core.spi.support.DefaultWebInterceptorChain;
import com.richie.component.web.core.spi.support.MutableWebRequestContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformProtectionInterceptorTest {

    private final PlatformProtectionProperties.RequestSize requestSizeConfig =
            new PlatformProtectionProperties.RequestSize();

    private final RequestSizeGuard sizeGuard = new RequestSizeGuard(1024, 256, 413, 431);
    private final LongLivedPathBypass bypass = new LongLivedPathBypass(List.of("/sse/**", "/ws/**"));
    private final PlatformProtectionInterceptor interceptor =
            new PlatformProtectionInterceptor(sizeGuard, bypass, requestSizeConfig);

    @Test
    void gatewayHeaderPresent_setsBypassAttribute() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header(PlatformProtectionInterceptor.GATEWAY_HEADER, "prod:cluster-a:gateway-7d4f")
                .build();
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        Boolean bypassFlag = ctx.attribute(PlatformProtectionInterceptor.GATEWAY_BYPASS_ATTRIBUTE);
        String gatewayId = ctx.attribute(PlatformProtectionInterceptor.GATEWAY_ID_ATTRIBUTE);
        assertThat(bypassFlag).isEqualTo(Boolean.TRUE);
        assertThat(gatewayId).isEqualTo("prod:cluster-a:gateway-7d4f");
        assertThat(ctx.isShortCircuited()).isFalse();
    }

    @Test
    void noGatewayHeader_doesNotSetBypassAttribute() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .build();
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat((Boolean) ctx.attribute(PlatformProtectionInterceptor.GATEWAY_BYPASS_ATTRIBUTE)).isNull();
        assertThat((String) ctx.attribute(PlatformProtectionInterceptor.GATEWAY_ID_ATTRIBUTE)).isNull();
    }

    @Test
    void blankGatewayHeader_doesNotSetBypassAttribute() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header(PlatformProtectionInterceptor.GATEWAY_HEADER, "   ")
                .build();
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat((Boolean) ctx.attribute(PlatformProtectionInterceptor.GATEWAY_BYPASS_ATTRIBUTE)).isNull();
    }

    @Test
    void bodyTooLarge_shortCircuitsWith413() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("POST").path("/api/v1/upload")
                .header("Content-Length", "2048")
                .build();
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(ctx.responseStatus()).isEqualTo(413);
        assertThat(ctx.shortCircuitBody()).contains("REQUEST_BODY_TOO_LARGE");
        assertThat(ctx.shortCircuitBody()).contains("\"code\":\"REQUEST_TOO_LARGE\"");
        assertThat(ctx.shortCircuitBody()).contains("limit=1024");
        assertThat(ctx.shortCircuitBody()).contains("actual=2048");
    }

    @Test
    void headerTooLarge_shortCircuitsWith431() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header("X-Huge", "x".repeat(300))
                .build();
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(ctx.responseStatus()).isEqualTo(431);
        assertThat(ctx.shortCircuitBody()).contains("REQUEST_HEADER_TOO_LARGE");
    }

    @Test
    void ssePath_setsLongLivedAttribute() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/sse/events")
                .build();
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat((Boolean) ctx.attribute(LongLivedPathBypass.LONG_LIVED_ATTRIBUTE)).isEqualTo(Boolean.TRUE);
    }

    @Test
    void normalPath_doesNotSetLongLivedAttribute() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .build();
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat((Boolean) ctx.attribute(LongLivedPathBypass.LONG_LIVED_ATTRIBUTE)).isNull();
    }

    @Test
    void callsChainProceed_whenAllChecksPass() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header("Accept", "application/json")
                .build();
        boolean[] proceeded = {false};
        WebInterceptorChain chain = new DefaultWebInterceptorChain(List.of(
                (c, ch) -> {
                    proceeded[0] = true;
                }));
        interceptor.intercept(ctx, chain);
        assertThat(proceeded[0]).isTrue();
    }

    @Test
    void getOrder_is100() {
        assertThat(interceptor.getOrder()).isEqualTo(PlatformProtectionInterceptor.ORDER);
        assertThat(PlatformProtectionInterceptor.ORDER).isEqualTo(100);
    }
}