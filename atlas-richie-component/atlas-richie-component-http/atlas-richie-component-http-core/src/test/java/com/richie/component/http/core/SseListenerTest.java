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
package com.richie.component.http.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SseListener} 默认方法 + 回调契约的单元测试。
 */
class SseListenerTest {

    @Test
    void defaultListenerNoOpsAllCallbacks() {
        SseListener listener = new SseListener() {
        };
        StubConnection conn = new StubConnection();

        listener.onOpen(conn);
        listener.onEvent(conn, SseEvent.of("x"));
        listener.onClosed(conn);
        listener.onFailure(conn, new RuntimeException("boom"));

        assertThat(conn.statusCode()).isEqualTo(200);
        assertThat(listener).isNotNull();
    }

    @Test
    void onOpenReceivesConnection() {
        AtomicReference<SseConnection> captured = new AtomicReference<>();
        SseListener listener = new SseListener() {
            @Override
            public void onOpen(SseConnection connection) {
                captured.set(connection);
            }
        };

        StubConnection conn = new StubConnection();
        listener.onOpen(conn);

        assertThat(captured.get()).isSameAs(conn);
    }

    @Test
    void onEventReceivesEventAndConnection() {
        AtomicReference<SseConnection> capturedConn = new AtomicReference<>();
        AtomicReference<SseEvent> capturedEvent = new AtomicReference<>();
        SseListener listener = new SseListener() {
            @Override
            public void onEvent(SseConnection connection, SseEvent event) {
                capturedConn.set(connection);
                capturedEvent.set(event);
            }
        };

        StubConnection conn = new StubConnection();
        SseEvent event = SseEvent.of("payload");
        listener.onEvent(conn, event);

        assertThat(capturedConn.get()).isSameAs(conn);
        assertThat(capturedEvent.get()).isSameAs(event);
    }

    @Test
    void onClosedReceivesConnection() {
        AtomicReference<SseConnection> captured = new AtomicReference<>();
        SseListener listener = new SseListener() {
            @Override
            public void onClosed(SseConnection connection) {
                captured.set(connection);
            }
        };

        StubConnection conn = new StubConnection();
        listener.onClosed(conn);

        assertThat(captured.get()).isSameAs(conn);
    }

    @Test
    void onFailureReceivesConnectionAndThrowable() {
        AtomicReference<SseConnection> capturedConn = new AtomicReference<>();
        AtomicReference<Throwable> capturedCause = new AtomicReference<>();
        SseListener listener = new SseListener() {
            @Override
            public void onFailure(SseConnection connection, Throwable cause) {
                capturedConn.set(connection);
                capturedCause.set(cause);
            }
        };

        StubConnection conn = new StubConnection();
        IllegalStateException ex = new IllegalStateException("broken");
        listener.onFailure(conn, ex);

        assertThat(capturedConn.get()).isSameAs(conn);
        assertThat(capturedCause.get()).isSameAs(ex);
    }

    @Test
    void listenersCanAccumulateEventSequence() {
        List<SseEvent> seen = new ArrayList<>();
        AtomicInteger openedCount = new AtomicInteger();
        AtomicInteger closedCount = new AtomicInteger();
        SseListener listener = new SseListener() {
            @Override
            public void onOpen(SseConnection connection) {
                openedCount.incrementAndGet();
            }

            @Override
            public void onEvent(SseConnection connection, SseEvent event) {
                seen.add(event);
            }

            @Override
            public void onClosed(SseConnection connection) {
                closedCount.incrementAndGet();
            }
        };
        StubConnection conn = new StubConnection();

        listener.onOpen(conn);
        listener.onEvent(conn, SseEvent.of("a"));
        listener.onEvent(conn, SseEvent.of("b"));
        listener.onClosed(conn);

        assertThat(openedCount.get()).isEqualTo(1);
        assertThat(seen).extracting(SseEvent::data).containsExactly("a", "b");
        assertThat(closedCount.get()).isEqualTo(1);
    }

    /**
     * 测试用 {@link SseConnection} 桩实现。
     */
    private static final class StubConnection implements SseConnection {

        private boolean open = true;

        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public java.util.Map<String, List<String>> headers() {
            return java.util.Map.of();
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }

}