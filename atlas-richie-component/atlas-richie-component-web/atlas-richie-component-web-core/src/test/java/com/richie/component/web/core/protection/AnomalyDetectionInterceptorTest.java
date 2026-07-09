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
package com.richie.component.web.core.protection;

import com.richie.component.web.core.spi.WebInterceptorChain;
import com.richie.component.web.core.spi.support.DefaultWebInterceptorChain;
import com.richie.component.web.core.spi.support.MutableWebRequestContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnomalyDetectionInterceptorTest {

    private static final String CODE = "ANOMALY_DETECTED";
    private static final String MSG = "异常客户端请求被拦截 (reason={reason})";

    private final AnomalyDetectionInterceptor noRules =
            new AnomalyDetectionInterceptor(List.of(), List.of(), 403, CODE, MSG);

    @Test
    void noRules_passesThrough() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header("User-Agent", "Mozilla/5.0")
                .build();
        boolean[] proceeded = {false};
        noRules.intercept(ctx, new DefaultWebInterceptorChain(List.of((c, ch) -> proceeded[0] = true)));
        assertThat(proceeded[0]).isTrue();
        assertThat(ctx.isShortCircuited()).isFalse();
    }

    @Test
    void botUserAgent_match_shortCircuitsWith403() throws Exception {
        AnomalyDetectionInterceptor it = new AnomalyDetectionInterceptor(
                List.of("curl/*", "python-requests/*"), List.of(), 403, CODE, MSG);
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header("User-Agent", "curl/7.85.0")
                .build();
        it.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(ctx.responseStatus()).isEqualTo(403);
        assertThat(ctx.shortCircuitBody()).contains("bot_user_agent");
        assertThat(ctx.shortCircuitBody()).contains("\"code\":\"ANOMALY_DETECTED\"");
    }

    @Test
    void botUserAgent_noMatch_passesThrough() throws Exception {
        AnomalyDetectionInterceptor it = new AnomalyDetectionInterceptor(
                List.of("curl/*"), List.of(), 403, CODE, MSG);
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header("User-Agent", "Mozilla/5.0")
                .build();
        boolean[] proceeded = {false};
        it.intercept(ctx, new DefaultWebInterceptorChain(List.of((c, ch) -> proceeded[0] = true)));
        assertThat(proceeded[0]).isTrue();
        assertThat(ctx.isShortCircuited()).isFalse();
    }

    @Test
    void botUserAgent_caseInsensitive() throws Exception {
        AnomalyDetectionInterceptor it = new AnomalyDetectionInterceptor(
                List.of("CURL/*"), List.of(), 403, CODE, MSG);
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header("User-Agent", "curl/7.85.0")
                .build();
        it.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat(ctx.isShortCircuited()).isTrue();
    }

    @Test
    void ipBlacklist_singleIp_match() throws Exception {
        AnomalyDetectionInterceptor it = new AnomalyDetectionInterceptor(
                List.of(), List.of("192.0.2.1"), 403, CODE, MSG);
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header("X-Forwarded-For", "192.0.2.1")
                .build();
        it.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(ctx.shortCircuitBody()).contains("ip_blacklisted");
    }

    @Test
    void ipBlacklist_cidr_match() throws Exception {
        AnomalyDetectionInterceptor it = new AnomalyDetectionInterceptor(
                List.of(), List.of("192.0.2.0/24"), 403, CODE, MSG);
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header("X-Forwarded-For", "192.0.2.42")
                .build();
        it.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat(ctx.isShortCircuited()).isTrue();
    }

    @Test
    void ipBlacklist_noMatch_passesThrough() throws Exception {
        AnomalyDetectionInterceptor it = new AnomalyDetectionInterceptor(
                List.of(), List.of("10.0.0.0/8"), 403, CODE, MSG);
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header("X-Forwarded-For", "203.0.113.5")
                .build();
        boolean[] proceeded = {false};
        it.intercept(ctx, new DefaultWebInterceptorChain(List.of((c, ch) -> proceeded[0] = true)));
        assertThat(proceeded[0]).isTrue();
    }

    @Test
    void ipBlacklist_fallsBackToXRealIp() throws Exception {
        AnomalyDetectionInterceptor it = new AnomalyDetectionInterceptor(
                List.of(), List.of("198.51.100.0/24"), 403, CODE, MSG);
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header("X-Real-IP", "198.51.100.7")
                .build();
        it.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat(ctx.isShortCircuited()).isTrue();
    }

    @Test
    void decisionAttribute_isSetOnDenial() throws Exception {
        AnomalyDetectionInterceptor it = new AnomalyDetectionInterceptor(
                List.of("curl/*"), List.of(), 403, CODE, MSG);
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header("User-Agent", "curl/7.85.0")
                .build();
        it.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> decision = ctx.attribute(AnomalyDetectionInterceptor.DECISION_ATTRIBUTE);
        assertThat(decision).isNotNull();
        assertThat(decision.get("type")).isEqualTo("bot");
        assertThat(decision.get("pattern")).isEqualTo("curl/*");
    }

    @Test
    void gatewayBypass_skipsAllChecks() throws Exception {
        AnomalyDetectionInterceptor it = new AnomalyDetectionInterceptor(
                List.of("curl/*"), List.of("192.0.2.0/24"), 403, CODE, MSG);
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header("User-Agent", "curl/7.85.0")
                .header("X-Forwarded-For", "192.0.2.42")
                .header(PlatformProtectionInterceptor.GATEWAY_HEADER, "prod:cluster-a:gw-1")
                .build();
        boolean[] proceeded = {false};
        it.intercept(ctx, new DefaultWebInterceptorChain(List.of((c, ch) -> proceeded[0] = true)));
        assertThat(proceeded[0]).isTrue();
        assertThat(ctx.isShortCircuited()).isFalse();
    }

    @Test
    void getOrder_is200() {
        assertThat(noRules.getOrder()).isEqualTo(200);
    }
}