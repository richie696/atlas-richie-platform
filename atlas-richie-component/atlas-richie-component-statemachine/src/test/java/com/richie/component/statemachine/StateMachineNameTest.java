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
package com.richie.component.statemachine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StateMachineName 单元测试
 *
 * @author richie696
 */
class StateMachineNameTest {

    enum TestStateMachine { order, payment }

    @Test
    void testOf_WithEnum() {
        StateMachineName name = StateMachineName.of(TestStateMachine.order);
        assertNotNull(name);
        assertEquals("order", name.getStateMachineName());
    }

    @Test
    void testOf_WithString() {
        StateMachineName name = StateMachineName.of("order");
        assertNotNull(name);
        assertEquals("order", name.getStateMachineName());
    }

    @Test
    void testStringStateMachineName() {
        StateMachineName.StringStateMachineName name = new StateMachineName.StringStateMachineName("payment");
        assertEquals("payment", name.getStateMachineName());
    }

    @Test
    void testEnumStateMachineName() {
        StateMachineName.EnumStateMachineName name = new StateMachineName.EnumStateMachineName(TestStateMachine.payment);
        assertEquals("payment", name.getStateMachineName());
    }

    @Test
    void testEquals() {
        StateMachineName name1 = StateMachineName.of("order");
        StateMachineName name2 = StateMachineName.of("order");
        StateMachineName name3 = StateMachineName.of("payment");

        assertEquals(name1, name2);
        assertNotEquals(name1, name3);
    }

    @Test
    void testHashCode() {
        StateMachineName name1 = StateMachineName.of("order");
        StateMachineName name2 = StateMachineName.of("order");

        assertEquals(name1.hashCode(), name2.hashCode());
    }

    @Test
    void testEnumStateMachineName_getStateMachineNameEnum() {
        StateMachineName.EnumStateMachineName name = new StateMachineName.EnumStateMachineName(TestStateMachine.order);
        assertEquals(TestStateMachine.order, name.getStateMachineNameEnum());
    }

    @Test
    void testEnumStateMachineName_toString() {
        StateMachineName.EnumStateMachineName name = new StateMachineName.EnumStateMachineName(TestStateMachine.order);
        assertEquals("EnumStateMachineName{stateMachineName=order}", name.toString());
    }

    @Test
    void testEnumStateMachineName_equals_Different() {
        StateMachineName.EnumStateMachineName name1 = new StateMachineName.EnumStateMachineName(TestStateMachine.order);
        StateMachineName.EnumStateMachineName name2 = new StateMachineName.EnumStateMachineName(TestStateMachine.payment);

        assertNotEquals(name1, name2);
    }

    @Test
    void testEnumStateMachineName_equals_NullAndDifferentClass() {
        StateMachineName.EnumStateMachineName name = new StateMachineName.EnumStateMachineName(TestStateMachine.order);

        assertNotEquals(name, null);
        assertNotEquals(name, "order");
    }

    @Test
    void testStringStateMachineName_toString() {
        StateMachineName.StringStateMachineName name = new StateMachineName.StringStateMachineName("order");
        assertEquals("StringStateMachineName{stateMachineName='order'}", name.toString());
    }
}

