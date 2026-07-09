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
package com.richie.component.web.core.business;

import com.richie.component.web.core.config.business.BusinessIntegrationProperties;
import com.richie.component.web.core.spi.support.DefaultWebInterceptorChain;
import com.richie.component.web.core.spi.support.MutableWebRequestContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyInterceptorTest {

    private final BusinessIntegrationProperties.Idempotency config = new BusinessIntegrationProperties.Idempotency();
    private final IdempotencyCache cache = new IdempotencyCache(60);
    private final IdempotencyInterceptor interceptor = new IdempotencyInterceptor(config, cache);

    @Test
    void firstRequest_passesThrough() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("POST").path("/api/v1/orders")
                .header("X-Idempotency-Key", "k1")
                .build();
        boolean[] proceeded = {false};
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of((c, ch) -> proceeded[0] = true)));
        assertThat(proceeded[0]).isTrue();
    }

    @Test
    void duplicateWithinTtl_deny() throws Exception {
        MutableWebRequestContext first = MutableWebRequestContext.builder()
                .method("POST").path("/api/v1/orders")
                .header("X-Idempotency-Key", "k1")
                .build();
        interceptor.intercept(first, new DefaultWebInterceptorChain(List.of()));

        MutableWebRequestContext second = MutableWebRequestContext.builder()
                .method("POST").path("/api/v1/orders")
                .header("X-Idempotency-Key", "k1")
                .build();
        interceptor.intercept(second, new DefaultWebInterceptorChain(List.of()));
        assertThat(second.isShortCircuited()).isTrue();
        assertThat(second.responseStatus()).isEqualTo(409);
        assertThat(second.shortCircuitBody()).contains("idempotent_replay");
    }

    @Test
    void noHeader_passesThrough() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("POST").path("/api/v1/orders")
                .build();
        boolean[] proceeded = {false};
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of((c, ch) -> proceeded[0] = true)));
        assertThat(proceeded[0]).isTrue();
    }

    @Test
    void blankHeader_passesThrough() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("POST").path("/api/v1/orders")
                .header("X-Idempotency-Key", "   ")
                .build();
        boolean[] proceeded = {false};
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of((c, ch) -> proceeded[0] = true)));
        assertThat(proceeded[0]).isTrue();
    }

    @Test
    void decisionAttribute_isSetOnDenial() throws Exception {
        MutableWebRequestContext first = MutableWebRequestContext.builder()
                .method("POST").path("/api/v1/orders")
                .header("X-Idempotency-Key", "k-decision")
                .build();
        interceptor.intercept(first, new DefaultWebInterceptorChain(List.of()));

        MutableWebRequestContext second = MutableWebRequestContext.builder()
                .method("POST").path("/api/v1/orders")
                .header("X-Idempotency-Key", "k-decision")
                .build();
        interceptor.intercept(second, new DefaultWebInterceptorChain(List.of()));
        @SuppressWarnings("unchecked")
        Map<String, Object> decision = (Map<String, Object>) second.attribute(IdempotencyInterceptor.DECISION_ATTRIBUTE);
        assertThat(decision).isNotNull();
        assertThat(decision.get("type")).isEqualTo("idempotency");
        assertThat(decision.get("key")).isEqualTo("k-decision");
    }

    @Test
    void differentKeys_independent() throws Exception {
        MutableWebRequestContext first = MutableWebRequestContext.builder()
                .method("POST").path("/api/v1/orders")
                .header("X-Idempotency-Key", "k-a")
                .build();
        interceptor.intercept(first, new DefaultWebInterceptorChain(List.of()));

        MutableWebRequestContext second = MutableWebRequestContext.builder()
                .method("POST").path("/api/v1/orders")
                .header("X-Idempotency-Key", "k-b")
                .build();
        boolean[] proceeded = {false};
        interceptor.intercept(second, new DefaultWebInterceptorChain(List.of((c, ch) -> proceeded[0] = true)));
        assertThat(proceeded[0]).isTrue();
    }

    @Test
    void getOrder_is270() {
        assertThat(interceptor.getOrder()).isEqualTo(270);
    }
}