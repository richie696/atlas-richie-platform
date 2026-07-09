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
package com.richie.component.web.core.protection;

import com.richie.component.web.core.config.protection.PlatformProtectionProperties;
import com.richie.component.web.core.servlet.InterceptingFilter;
import com.richie.component.web.core.spi.WebInterceptor;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformProtectionIntegrationTest {

    private final PlatformProtectionProperties.RequestSize requestSizeConfig =
            new PlatformProtectionProperties.RequestSize();

    private final RequestSizeGuard sizeGuard = new RequestSizeGuard(1024, 256, 413, 431);
    private final LongLivedPathBypass bypass = new LongLivedPathBypass(List.of("/sse/**"));
    private final PlatformProtectionInterceptor interceptor =
            new PlatformProtectionInterceptor(sizeGuard, bypass, requestSizeConfig);

    @Test
    void throughFilter_normalRequest_keepsDownstreamFilterInvoked() throws Exception {
        InterceptingFilter filter = new InterceptingFilter(List.of(interceptor));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/users");
        req.setRequestURI("/api/v1/users");
        req.addHeader("Accept", "application/json");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain downChain = new MockFilterChain();

        filter.doFilter(req, resp, downChain);

        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(downChain.getRequest()).isNotNull();
    }

    @Test
    void throughFilter_bodyTooLarge_returns413WithoutCallingDownstream() throws Exception {
        InterceptingFilter filter = new InterceptingFilter(List.of(interceptor));
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/upload");
        req.setRequestURI("/api/v1/upload");
        req.addHeader("Content-Length", "999999");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain downChain = new MockFilterChain();

        filter.doFilter(req, resp, downChain);

        assertThat(resp.getStatus()).isEqualTo(413);
        assertThat(resp.getContentAsString()).contains("REQUEST_BODY_TOO_LARGE");
        assertThat(resp.getContentAsString()).contains("\"code\":\"REQUEST_TOO_LARGE\"");
        assertThat(downChain.getRequest()).isNull();
    }

    @Test
    void throughFilter_gatewayHeader_attributesWrittenAndDownstreamStillCalled() throws Exception {
        InterceptingFilter filter = new InterceptingFilter(List.of(interceptor));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/users");
        req.setRequestURI("/api/v1/users");
        req.addHeader("X-Forwarded-From-Gateway", "dev:cluster-a:gateway-7d4f");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain downChain = new MockFilterChain();

        filter.doFilter(req, resp, downChain);

        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(downChain.getRequest()).isNotNull();
    }

    @Test
    void throughFilter_ssePath_downstreamCalledButLongLivedFlagStored() throws Exception {
        List<String> trace = new ArrayList<>();
        WebInterceptor tail = (ctx, chain) -> {
            Object longLived = ctx.attribute(LongLivedPathBypass.LONG_LIVED_ATTRIBUTE);
            trace.add("tail:" + (longLived == Boolean.TRUE ? "long-lived" : "short"));
            chain.proceed(ctx);
        };
        InterceptingFilter filter = new InterceptingFilter(List.of(interceptor, tail));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/sse/events");
        req.setRequestURI("/sse/events");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain downChain = new MockFilterChain();

        filter.doFilter(req, resp, downChain);

        assertThat(trace).containsExactly("tail:long-lived");
        assertThat(downChain.getRequest()).isNotNull();
    }
}