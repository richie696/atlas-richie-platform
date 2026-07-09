/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.statemachine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StateMachineEvent 单元测试
 *
 * @author richie696
 */
class StateMachineEventTest {

    enum TestEvent { CONFIRM, CANCEL }

    @Test
    void testOf_WithEnum() {
        StateMachineEvent event = StateMachineEvent.of(TestEvent.CONFIRM);
        assertNotNull(event);
        assertEquals("CONFIRM", event.getEventName());
    }

    @Test
    void testOf_WithString() {
        StateMachineEvent event = StateMachineEvent.of("CONFIRM");
        assertNotNull(event);
        assertEquals("CONFIRM", event.getEventName());
    }

    @Test
    void testStringEvent() {
        StateMachineEvent.StringEvent event = new StateMachineEvent.StringEvent("CANCEL");
        assertEquals("CANCEL", event.getEventName());
    }

    @Test
    void testEnumEvent() {
        StateMachineEvent.EnumEvent event = new StateMachineEvent.EnumEvent(TestEvent.CANCEL);
        assertEquals("CANCEL", event.getEventName());
    }

    @Test
    void testEquals() {
        StateMachineEvent event1 = StateMachineEvent.of("CONFIRM");
        StateMachineEvent event2 = StateMachineEvent.of("CONFIRM");
        StateMachineEvent event3 = StateMachineEvent.of("CANCEL");

        assertEquals(event1, event2);
        assertNotEquals(event1, event3);
    }

    @Test
    void testHashCode() {
        StateMachineEvent event1 = StateMachineEvent.of("CONFIRM");
        StateMachineEvent event2 = StateMachineEvent.of("CONFIRM");

        assertEquals(event1.hashCode(), event2.hashCode());
    }

    @Test
    void testEnumEvent_getEvent() {
        StateMachineEvent.EnumEvent event = new StateMachineEvent.EnumEvent(TestEvent.CONFIRM);
        assertEquals(TestEvent.CONFIRM, event.getEvent());
    }

    @Test
    void testEnumEvent_toString() {
        StateMachineEvent.EnumEvent event = new StateMachineEvent.EnumEvent(TestEvent.CONFIRM);
        assertEquals("EnumEvent{event=CONFIRM}", event.toString());
    }

    @Test
    void testEnumEvent_equals_DifferentEvent() {
        StateMachineEvent.EnumEvent event1 = new StateMachineEvent.EnumEvent(TestEvent.CONFIRM);
        StateMachineEvent.EnumEvent event2 = new StateMachineEvent.EnumEvent(TestEvent.CANCEL);

        assertNotEquals(event1, event2);
    }

    @Test
    void testEnumEvent_equals_NullAndDifferentClass() {
        StateMachineEvent.EnumEvent event = new StateMachineEvent.EnumEvent(TestEvent.CONFIRM);

        assertNotEquals(event, null);
        assertNotEquals(event, "CONFIRM");
    }

    @Test
    void testStringEvent_toString() {
        StateMachineEvent.StringEvent event = new StateMachineEvent.StringEvent("CONFIRM");
        assertEquals("StringEvent{eventName='CONFIRM'}", event.toString());
    }
}

