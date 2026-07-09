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
package com.richie.component.web.core.degrade;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DegradeResult} 不可变 record + {@link DegradeStrategy} SPI 默认实现测试。
 */
class DegradeStrategyTest {

    // ─────────────────── DegradeResult ───────────────────

    @Test
    void degradeResult_of_compact() {
        DegradeResult r = DegradeResult.of(503, "{\"error\":\"x\"}", "stub");
        assertThat(r.status()).isEqualTo(503);
        assertThat(r.body()).isEqualTo("{\"error\":\"x\"}");
        assertThat(r.headers()).isEmpty();
        assertThat(r.strategyName()).isEqualTo("stub");
    }

    @Test
    void degradeResult_withHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Retry-After", "5");
        DegradeResult r = DegradeResult.of(429, "rate-limited", headers, "rl-stub");
        assertThat(r.headers()).containsEntry("Retry-After", "5");
    }

    @Test
    void degradeResult_headersImmutable() {
        DegradeResult r = DegradeResult.of(503, "x", "stub");
        assertThatThrownBy(() -> r.headers().put("X", "Y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void degradeResult_invalidStatus() {
        assertThatThrownBy(() -> DegradeResult.of(99, "x", "stub"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DegradeResult.of(600, "x", "stub"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void degradeResult_nullBody_throws() {
        assertThatThrownBy(() -> DegradeResult.of(503, null, "stub"))
                .isInstanceOf(NullPointerException.class);
    }

    // ─────────────────── DegradeStrategy 匿名实现 ───────────────────

    @Test
    void strategy_anonymousImpl() {
        DegradeStrategy s = new DegradeStrategy() {
            @Override public String name() { return "test"; }
            @Override public Set<Trigger> triggers() { return Set.of(Trigger.EXCEPTION); }
            @Override public int order() { return 0; }
            @Override public boolean matches(Trigger t) { return t == Trigger.EXCEPTION; }
            @Override public DegradeResult build(Trigger t, Map<String, Object> ctx) {
                return DegradeResult.of(500, "{\"error\":\"exception\"}", "test");
            }
        };
        assertThat(s.name()).isEqualTo("test");
        assertThat(s.triggers()).containsExactly(Trigger.EXCEPTION);
        assertThat(s.order()).isEqualTo(0);
        assertThat(s.matches(Trigger.EXCEPTION)).isTrue();
        assertThat(s.matches(Trigger.CUSTOM)).isFalse();
        DegradeResult r = s.build(Trigger.EXCEPTION, Map.of("path", "/x"));
        assertThat(r.status()).isEqualTo(500);
        assertThat(r.body()).contains("exception");
        assertThat(r.strategyName()).isEqualTo("test");
    }
}