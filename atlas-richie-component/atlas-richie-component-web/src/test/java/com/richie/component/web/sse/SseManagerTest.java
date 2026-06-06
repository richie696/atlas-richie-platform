package com.richie.component.web.sse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class SseManagerTest {

    @AfterEach
    void cleanup() {
        SseManager.instance.removeEmitter("client-1");
    }

    @Test
    void createEmitter_putsAndReturnsEmitter() throws Exception {
        SseEmitter emitter = SseManager.instance.createEmitter("client-1");

        assertThat(emitter).isNotNull();
        assertThat(SseManager.instance.containsEmitter("client-1")).isTrue();
        assertThat(SseManager.instance.getEmitter("client-1")).isSameAs(emitter);
    }

    @Test
    void send_returnsFalseWhenClientMissing() {
        assertThat(SseManager.instance.send("missing", "hello")).isFalse();
    }

    @Test
    void createEmitter_reusesExistingConnection() throws Exception {
        SseEmitter first = SseManager.instance.createEmitter("client-2");
        SseEmitter second = SseManager.instance.createEmitter("client-2");

        assertThat(second).isSameAs(first);
        SseManager.instance.removeEmitter("client-2");
    }

    @Test
    void send_returnsFalseWhenEmitterThrows() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new RuntimeException("broken")).when(emitter).send(any(Object.class));
        SseManager.instance.putEmitter("client-4", emitter);

        assertThat(SseManager.instance.send("client-4", "msg")).isFalse();
        SseManager.instance.removeEmitter("client-4");
    }
}
