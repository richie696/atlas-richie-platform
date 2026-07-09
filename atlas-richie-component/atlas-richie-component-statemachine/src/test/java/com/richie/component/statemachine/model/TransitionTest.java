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
package com.richie.component.statemachine.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transition 单元测试
 *
 * @author richie696
 */
class TransitionTest {

    @Test
    void testDefaultConstructor() {
        Transition transition = new Transition();
        assertNull(transition.getName());
        assertNull(transition.getFromState());
        assertNull(transition.getToState());
        assertNull(transition.getEvent());
        assertNull(transition.getCondition());
        assertNull(transition.getAction());
        assertEquals(0, transition.getPriority());
        assertNull(transition.getAttributes());
    }

    @Test
    void testConstructorWithNameAndStates() {
        Transition transition = new Transition("confirm", "PENDING", "CONFIRMED");
        assertEquals("confirm", transition.getName());
        assertEquals("PENDING", transition.getFromState());
        assertEquals("CONFIRMED", transition.getToState());
        assertNull(transition.getEvent());
    }

    @Test
    void testConstructorWithNameStatesAndEvent() {
        Transition transition = new Transition("confirm", "PENDING", "CONFIRMED", "CONFIRM");
        assertEquals("confirm", transition.getName());
        assertEquals("PENDING", transition.getFromState());
        assertEquals("CONFIRMED", transition.getToState());
        assertEquals("CONFIRM", transition.getEvent());
    }

    @Test
    void testSettersAndGetters() {
        Transition transition = new Transition();
        transition.setName("test_transition");
        transition.setFromState("PENDING");
        transition.setToState("CONFIRMED");
        transition.setEvent("CONFIRM");
        transition.setCondition("amount > 0");
        transition.setAction("context.setAttribute('completed', true)");
        transition.setPriority(10);
        
        assertEquals("test_transition", transition.getName());
        assertEquals("PENDING", transition.getFromState());
        assertEquals("CONFIRMED", transition.getToState());
        assertEquals("CONFIRM", transition.getEvent());
        assertEquals("amount > 0", transition.getCondition());
        assertEquals("context.setAttribute('completed', true)", transition.getAction());
        assertEquals(10, transition.getPriority());
    }

    @Test
    void testAttributes() {
        Transition transition = new Transition();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("reopen", true);
        attributes.put("priority", 5);
        transition.setAttributes(attributes);
        
        assertNotNull(transition.getAttributes());
        assertEquals(true, transition.getAttributes().get("reopen"));
        assertEquals(5, transition.getAttributes().get("priority"));
    }

    @Test
    void testAttributes_ReopenFlag() {
        Transition transition = new Transition();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("reopen", true);
        transition.setAttributes(attributes);
        
        assertTrue(Boolean.TRUE.equals(transition.getAttributes().get("reopen")));
    }

    @Test
    void testEqualsAndHashCode() {
        Transition t1 = new Transition("confirm", "PENDING", "CONFIRMED", "CONFIRM");
        Transition t2 = new Transition("confirm", "PENDING", "CONFIRMED", "CONFIRM");
        Transition t3 = new Transition("cancel", "PENDING", "CANCELLED", "CANCEL");
        
        assertEquals(t1, t2);
        assertNotEquals(t1, t3);
        assertEquals(t1.hashCode(), t2.hashCode());
    }
}

