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
package com.richie.component.web.core.spi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link WebFilterDecision} 的基本契约测试。
 */
class WebFilterDecisionTest {

    @Test
    void rateLimitDeny_factoryBuildsExpectedFields() {
        WebFilterDecision d = WebFilterDecision.rateLimitDeny("RateLimitInterceptor", "user-42", 429);

        assertThat(d.interceptor()).isEqualTo("RateLimitInterceptor");
        assertThat(d.key()).isEqualTo("user-42");
        assertThat(d.status()).isEqualTo(429);
        assertThat(d.reason()).isEqualTo("rate_limit.exceeded");
        assertThat(d.isDeny()).isTrue();
    }

    @Test
    void circuitBreakerDeny_factoryBuildsExpectedFields() {
        WebFilterDecision d = WebFilterDecision.circuitBreakerDeny("CircuitBreakerInterceptor", "tenant-a", 503);

        assertThat(d.interceptor()).isEqualTo("CircuitBreakerInterceptor");
        assertThat(d.key()).isEqualTo("tenant-a");
        assertThat(d.status()).isEqualTo(503);
        assertThat(d.reason()).isEqualTo("circuit_breaker.open");
        assertThat(d.isDeny()).isTrue();
    }

    @Test
    void isDeny_returnsFalseFor2xx() {
        WebFilterDecision d = new WebFilterDecision("AllowAll", null, 200, "ok");
        assertThat(d.isDeny()).isFalse();
    }

    @Test
    void isDeny_returnsFalseFor3xx() {
        WebFilterDecision d = new WebFilterDecision("Redirect", null, 302, "redirect");
        assertThat(d.isDeny()).isFalse();
    }

    @Test
    void isDeny_returnsTrueFor5xx() {
        WebFilterDecision d = new WebFilterDecision("Error", null, 500, "boom");
        assertThat(d.isDeny()).isTrue();
    }
}