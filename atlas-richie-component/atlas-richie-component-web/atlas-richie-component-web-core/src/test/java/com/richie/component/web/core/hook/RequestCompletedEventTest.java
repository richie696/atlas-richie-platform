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