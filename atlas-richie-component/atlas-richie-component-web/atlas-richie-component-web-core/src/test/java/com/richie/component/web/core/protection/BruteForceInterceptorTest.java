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

import com.richie.component.web.core.spi.support.DefaultWebInterceptorChain;
import com.richie.component.web.core.spi.support.MutableWebRequestContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BruteForceInterceptorTest {

    private static final String CODE = "BRUTE_FORCE";
    private static final String MSG = "登录尝试过于频繁，已锁定 {lockout} 秒";

    private final LoginAttemptTracker tracker = new LoginAttemptTracker(60, 5, 900);
    private final BruteForceInterceptor interceptor =
            new BruteForceInterceptor(tracker, 429, CODE, MSG, 900);

    @Test
    void nonLoginPath_passesThrough() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users").build();
        boolean[] proceeded = {false};
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of((c, ch) -> proceeded[0] = true)));
        assertThat(proceeded[0]).isTrue();
        assertThat(ctx.isShortCircuited()).isFalse();
    }

    @Test
    void nonPostOnLoginPath_passesThrough() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/login").build();
        boolean[] proceeded = {false};
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of((c, ch) -> proceeded[0] = true)));
        assertThat(proceeded[0]).isTrue();
    }

    @Test
    void loginPathWithoutClientKey_passesThrough() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("POST").path("/login").build();
        boolean[] proceeded = {false};
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of((c, ch) -> proceeded[0] = true)));
        assertThat(proceeded[0]).isTrue();
    }

    @Test
    void lockedKey_shortCircuitsWith429() throws Exception {
        String key = "user-1";
        for (int i = 0; i < 5; i++) {
            tracker.recordFailure(key);
        }
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("POST").path("/login")
                .build();
        ctx.setClientKey(key);
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(ctx.responseStatus()).isEqualTo(429);
        assertThat(ctx.shortCircuitBody()).contains("900 秒");
        assertThat(ctx.shortCircuitBody()).contains("\"code\":\"BRUTE_FORCE\"");
    }

    @Test
    void unlockOnSuccess_resetsAttempts() throws Exception {
        String key = "user-1";
        for (int i = 0; i < 4; i++) {
            tracker.recordFailure(key);
        }
        tracker.recordSuccess(key);
        for (int i = 0; i < 4; i++) {
            tracker.recordFailure(key);
        }
        assertThat(tracker.isLocked(key)).isFalse();
    }

    @Test
    void decisionAttribute_isSetOnLocked() throws Exception {
        String key = "user-1";
        for (int i = 0; i < 5; i++) {
            tracker.recordFailure(key);
        }
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("POST").path("/login").build();
        ctx.setClientKey(key);
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> decision = ctx.attribute(BruteForceInterceptor.DECISION_ATTRIBUTE);
        assertThat(decision).isNotNull();
        assertThat(decision.get("type")).isEqualTo("locked");
        assertThat(decision.get("key")).isEqualTo(key);
    }

    @Test
    void gatewayBypass_skipsLockedCheck() throws Exception {
        String key = "user-1";
        for (int i = 0; i < 5; i++) {
            tracker.recordFailure(key);
        }
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("POST").path("/login")
                .header(PlatformProtectionInterceptor.GATEWAY_HEADER, "prod:cluster-a:gw-1")
                .build();
        ctx.setClientKey(key);
        boolean[] proceeded = {false};
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of((c, ch) -> proceeded[0] = true)));
        assertThat(proceeded[0]).isTrue();
        assertThat(ctx.isShortCircuited()).isFalse();
    }

    @Test
    void getOrder_is210() {
        assertThat(interceptor.getOrder()).isEqualTo(210);
    }
}