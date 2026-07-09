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
package com.richie.component.statemachine.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StateMachineModel 单元测试
 *
 * @author richie696
 */
class StateMachineModelTest {

    private StateMachineModel stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new StateMachineModel("order", "订单状态机");
        stateMachine.setInitialState("PENDING");
    }

    @Test
    void testDefaultConstructor() {
        StateMachineModel sm = new StateMachineModel();
        assertNull(sm.getName());
        assertNull(sm.getDescription());
        assertNull(sm.getInitialState());
        assertNotNull(sm.getStates());
        assertNotNull(sm.getTransitions());
        assertTrue(sm.getStates().isEmpty());
        assertTrue(sm.getTransitions().isEmpty());
        assertEquals("1.0.0", sm.getVersion());
    }

    @Test
    void testConstructorWithName() {
        StateMachineModel sm = new StateMachineModel("payment");
        assertEquals("payment", sm.getName());
        assertNull(sm.getDescription());
    }

    @Test
    void testConstructorWithNameAndDescription() {
        StateMachineModel sm = new StateMachineModel("payment", "支付状态机");
        assertEquals("payment", sm.getName());
        assertEquals("支付状态机", sm.getDescription());
    }

    @Test
    void testAddState() {
        State state = new State("PENDING", "待确认", State.StateType.INITIAL);
        stateMachine.addState(state);
        
        assertEquals(1, stateMachine.getStates().size());
        assertEquals(state, stateMachine.getStates().get(0));
    }

    @Test
    void testAddTransition() {
        Transition transition = new Transition("confirm", "PENDING", "CONFIRMED", "CONFIRM");
        stateMachine.addTransition(transition);
        
        assertEquals(1, stateMachine.getTransitions().size());
        assertEquals(transition, stateMachine.getTransitions().get(0));
    }

    @Test
    void testGetState() {
        State pending = new State("PENDING", "待确认");
        State confirmed = new State("CONFIRMED", "已确认");
        stateMachine.addState(pending);
        stateMachine.addState(confirmed);
        
        assertEquals(pending, stateMachine.getState("PENDING"));
        assertEquals(confirmed, stateMachine.getState("CONFIRMED"));
        assertNull(stateMachine.getState("NON_EXISTENT"));
    }

    @Test
    void testGetTransitions() {
        Transition t1 = new Transition("confirm", "PENDING", "CONFIRMED", "CONFIRM");
        Transition t2 = new Transition("cancel", "PENDING", "CANCELLED", "CANCEL");
        Transition t3 = new Transition("complete", "CONFIRMED", "COMPLETED", "COMPLETE");
        
        stateMachine.addTransition(t1);
        stateMachine.addTransition(t2);
        stateMachine.addTransition(t3);
        
        List<Transition> transitions = stateMachine.getTransitions("PENDING", "CONFIRM");
        assertEquals(1, transitions.size());
        assertEquals(t1, transitions.get(0));
        
        transitions = stateMachine.getTransitions("PENDING", "CANCEL");
        assertEquals(1, transitions.size());
        assertEquals(t2, transitions.get(0));
        
        transitions = stateMachine.getTransitions("PENDING", null);
        assertEquals(2, transitions.size());
        
        transitions = stateMachine.getTransitions("NON_EXISTENT", "CONFIRM");
        assertTrue(transitions.isEmpty());
    }

    @Test
    void testSettersAndGetters() {
        stateMachine.setName("payment");
        stateMachine.setDescription("支付状态机");
        stateMachine.setInitialState("PENDING");
        stateMachine.setVersion("2.0.0");
        
        assertEquals("payment", stateMachine.getName());
        assertEquals("支付状态机", stateMachine.getDescription());
        assertEquals("PENDING", stateMachine.getInitialState());
        assertEquals("2.0.0", stateMachine.getVersion());
    }
}

