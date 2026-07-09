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
package com.richie.component.web.core.hook;

import com.richie.component.web.core.spi.support.MutableWebRequestContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCompletedEventTest {

    @Test
    void of_extractsAllFields() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("POST").path("/api/v1/users")
                .build();
        ctx.setResponseStatus(201);
        ctx.setClientKey("client-1");
        ctx.setTraceId("trace-xyz");
        long start = System.nanoTime();
        ctx.close(); // simulate close
        RequestCompletedEvent event = RequestCompletedEvent.of(ctx, System.nanoTime());
        assertThat(event.method()).isEqualTo("POST");
        assertThat(event.path()).isEqualTo("/api/v1/users");
        assertThat(event.responseStatus()).isEqualTo(201);
        assertThat(event.clientKey()).isEqualTo("client-1");
        assertThat(event.traceId()).isEqualTo("trace-xyz");
        assertThat(event.shortCircuited()).isFalse();
        assertThat(event.durationMillis()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void durationMillis_nonNegative() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/")
                .build();
        RequestCompletedEvent event = RequestCompletedEvent.of(ctx, System.nanoTime() + 5_000_000L);
        assertThat(event.durationMillis()).isGreaterThanOrEqualTo(0L);
    }
}