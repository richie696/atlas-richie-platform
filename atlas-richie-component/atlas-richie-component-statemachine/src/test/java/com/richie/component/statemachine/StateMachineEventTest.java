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
}

