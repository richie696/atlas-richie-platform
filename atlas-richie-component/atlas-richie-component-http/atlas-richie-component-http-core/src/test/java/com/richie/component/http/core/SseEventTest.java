package com.richie.component.http.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SseEvent} record + 静态工厂的单元测试。
 */
class SseEventTest {

    @Test
    void recordCarriesAllFields() {
        Duration retry = Duration.ofMillis(500);
        SseEvent event = new SseEvent("1", "update", "{\"x\":1}", retry);

        assertThat(event.id()).isEqualTo("1");
        assertThat(event.event()).isEqualTo("update");
        assertThat(event.data()).isEqualTo("{\"x\":1}");
        assertThat(event.retry()).isEqualTo(retry);
    }

    @Test
    void recordAllowsNullFields() {
        SseEvent event = new SseEvent(null, null, null, null);

        assertThat(event.id()).isNull();
        assertThat(event.event()).isNull();
        assertThat(event.data()).isNull();
        assertThat(event.retry()).isNull();
    }

    @Test
    void ofFactoryProducesMessageEventWithDataOnly() {
        SseEvent event = SseEvent.of("hello");

        assertThat(event.id()).isNull();
        assertThat(event.event()).isEqualTo(SseEvent.DEFAULT_EVENT_NAME);
        assertThat(event.data()).isEqualTo("hello");
        assertThat(event.retry()).isNull();
    }

    @Test
    void ofFactoryAcceptsNullData() {
        SseEvent event = SseEvent.of(null);

        assertThat(event.data()).isNull();
        assertThat(event.event()).isEqualTo(SseEvent.DEFAULT_EVENT_NAME);
    }

    @Test
    void defaultEventNameConstantIsMessage() {
        assertThat(SseEvent.DEFAULT_EVENT_NAME).isEqualTo("message");
    }

    @Test
    void recordEqualityAndHashCodeWork() {
        SseEvent a = new SseEvent("1", "msg", "data", null);
        SseEvent b = new SseEvent("1", "msg", "data", null);
        SseEvent c = new SseEvent("2", "msg", "data", null);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }

}