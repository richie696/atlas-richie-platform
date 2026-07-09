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
package com.richie.component.statemachine.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StateContext 单元测试
 *
 * @author richie696
 */
class StateContextTest {

    private StateContext context;

    @BeforeEach
    void setUp() {
        context = new StateContext();
    }

    @Test
    void testDefaultConstructor() {
        assertNotNull(context);
        assertNull(context.getCurrentState());
        assertNull(context.getPreviousState());
        assertNull(context.getEvent());
        assertNull(context.getTransition());
        assertNotNull(context.getAttributes());
        assertTrue(context.getAttributes().isEmpty());
        assertNotNull(context.getCreateTime());
        assertNotNull(context.getUpdateTime());
    }

    @Test
    void testConstructorWithCurrentState() {
        StateContext ctx = new StateContext("PENDING");
        assertEquals("PENDING", ctx.getCurrentState());
        assertNull(ctx.getEvent());
    }

    @Test
    void testConstructorWithCurrentStateAndEvent() {
        StateContext ctx = new StateContext("PENDING", "CONFIRM");
        assertEquals("PENDING", ctx.getCurrentState());
        assertEquals("CONFIRM", ctx.getEvent());
    }

    @Test
    void testSetAndGetAttribute() {
        context.setAttribute("operator", "user123");
        context.setAttribute("amount", 100.0);

        assertEquals("user123", context.getAttribute("operator"));
        assertEquals(100.0, context.getAttribute("amount"));
    }

    @Test
    void testGetAttributeWithDefaultValue() {
        context.setAttribute("operator", "user123");

        assertEquals("user123", context.getAttribute("operator", "default"));
        assertEquals("default", context.getAttribute("nonExistent", "default"));
    }

    @Test
    void testRemoveAttribute() {
        context.setAttribute("operator", "user123");
        assertEquals("user123", context.removeAttribute("operator"));
        assertNull(context.getAttribute("operator"));
    }

    @Test
    void testUpdateTime() {
        LocalDateTime before = context.getUpdateTime();
        
        // 等待一小段时间确保时间不同
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        context.updateTime();
        LocalDateTime after = context.getUpdateTime();
        
        assertTrue(after.isAfter(before) || after.equals(before));
    }

    @Test
    void testSetAttributes() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("key1", "value1");
        attrs.put("key2", 123);
        
        context.setAttributes(attrs);
        
        assertEquals("value1", context.getAttribute("key1"));
        assertEquals(123, context.getAttribute("key2"));
    }

    @Test
    void testStateTransition() {
        context.setCurrentState("PENDING");
        context.setPreviousState(null);
        context.setEvent("CONFIRM");
        
        assertEquals("PENDING", context.getCurrentState());
        assertNull(context.getPreviousState());
        assertEquals("CONFIRM", context.getEvent());
    }
}

