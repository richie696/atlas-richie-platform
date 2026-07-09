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

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HookBusTest {

    @Test
    void subscribeAndPublish_fanOutToAllSubscribers() {
        HookBus bus = new DefaultHookBus();
        AtomicInteger count = new AtomicInteger();
        bus.subscribe(RequestCompletedEvent.class, e -> count.incrementAndGet());
        bus.subscribe(RequestCompletedEvent.class, e -> count.incrementAndGet());
        bus.publish(new RequestCompletedEvent("GET", "/", 200, 0L, 10L, false, false, null, "trace-1"));
        assertThat(count.get()).isEqualTo(2);
    }

    @Test
    void publish_nullEvent_doesNothing() {
        HookBus bus = new DefaultHookBus();
        AtomicInteger count = new AtomicInteger();
        bus.subscribe(RequestCompletedEvent.class, e -> count.incrementAndGet());
        bus.publish(null);
        assertThat(count.get()).isZero();
    }

    @Test
    void publish_noSubscribers_doesNothing() {
        HookBus bus = new DefaultHookBus();
        bus.publish(new RequestCompletedEvent("GET", "/", 200, 0L, 10L, false, false, null, "trace-1"));
    }

    @Test
    void subscriberException_doesNotPropagate() {
        HookBus bus = new DefaultHookBus();
        AtomicInteger count = new AtomicInteger();
        bus.subscribe(RequestCompletedEvent.class, e -> { throw new RuntimeException("boom"); });
        bus.subscribe(RequestCompletedEvent.class, e -> count.incrementAndGet());
        bus.publish(new RequestCompletedEvent("GET", "/", 200, 0L, 10L, false, false, null, "trace-1"));
        assertThat(count.get()).isEqualTo(1);
    }

    @Test
    void unsubscribe_stopsDelivery() {
        HookBus bus = new DefaultHookBus();
        AtomicInteger count = new AtomicInteger();
        HookBus.Subscription sub = bus.subscribe(RequestCompletedEvent.class, e -> count.incrementAndGet());
        bus.publish(new RequestCompletedEvent("GET", "/", 200, 0L, 10L, false, false, null, "trace-1"));
        sub.close();
        bus.publish(new RequestCompletedEvent("GET", "/", 200, 0L, 10L, false, false, null, "trace-2"));
        assertThat(count.get()).isEqualTo(1);
    }

    @Test
    void differentEventTypes_isolated() {
        HookBus bus = new DefaultHookBus();
        AtomicInteger rc = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();
        bus.subscribe(RequestCompletedEvent.class, e -> rc.incrementAndGet());
        bus.subscribe(HookBusTestMarker.class, e -> other.incrementAndGet());
        bus.publish(new RequestCompletedEvent("GET", "/", 200, 0L, 10L, false, false, null, "trace-1"));
        assertThat(rc.get()).isEqualTo(1);
        assertThat(other.get()).isZero();
    }

    @Test
    void diagnosticView_showsSubscriberCounts() {
        HookBus bus = new DefaultHookBus();
        bus.subscribe(RequestCompletedEvent.class, e -> {});
        bus.subscribe(RequestCompletedEvent.class, e -> {});
        bus.subscribe(HookBusTestMarker.class, e -> {});
        assertThat(bus.diagnosticView()).containsExactlyInAnyOrder(
                "RequestCompletedEvent→2", "HookBusTestMarker→1");
    }

    record HookBusTestMarker(String payload) implements HookEvent {}
}