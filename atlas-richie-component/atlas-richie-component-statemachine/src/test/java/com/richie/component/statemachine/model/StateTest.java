package com.richie.component.statemachine.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * State 单元测试
 *
 * @author richie696
 */
class StateTest {

    @Test
    void testDefaultConstructor() {
        State state = new State();
        assertNull(state.getName());
        assertNull(state.getDescription());
        assertEquals(State.StateType.NORMAL, state.getType());
    }

    @Test
    void testConstructorWithName() {
        State state = new State("PENDING");
        assertEquals("PENDING", state.getName());
        assertNull(state.getDescription());
        assertEquals(State.StateType.NORMAL, state.getType());
    }

    @Test
    void testConstructorWithNameAndDescription() {
        State state = new State("PENDING", "待确认");
        assertEquals("PENDING", state.getName());
        assertEquals("待确认", state.getDescription());
        assertEquals(State.StateType.NORMAL, state.getType());
    }

    @Test
    void testConstructorWithAllFields() {
        State state = new State("PENDING", "待确认", State.StateType.INITIAL);
        assertEquals("PENDING", state.getName());
        assertEquals("待确认", state.getDescription());
        assertEquals(State.StateType.INITIAL, state.getType());
    }

    @Test
    void testStateTypeEnum() {
        assertEquals(4, State.StateType.values().length);
        assertTrue(State.StateType.NORMAL != null);
        assertTrue(State.StateType.INITIAL != null);
        assertTrue(State.StateType.FINAL != null);
        assertTrue(State.StateType.ERROR != null);
    }

    @Test
    void testSettersAndGetters() {
        State state = new State();
        state.setName("CONFIRMED");
        state.setDescription("已确认");
        state.setType(State.StateType.NORMAL);
        
        assertEquals("CONFIRMED", state.getName());
        assertEquals("已确认", state.getDescription());
        assertEquals(State.StateType.NORMAL, state.getType());
    }

    @Test
    void testEqualsAndHashCode() {
        State state1 = new State("PENDING", "待确认");
        State state2 = new State("PENDING", "待确认");
        State state3 = new State("CONFIRMED", "已确认");
        
        assertEquals(state1, state2);
        assertNotEquals(state1, state3);
        assertEquals(state1.hashCode(), state2.hashCode());
    }
}

