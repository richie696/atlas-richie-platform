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