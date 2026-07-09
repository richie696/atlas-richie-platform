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
package com.richie.component.statemachine.storage;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StateHistory 单元测试
 *
 * @author richie696
 */
class StateHistoryTest {

    @Test
    void testDefaultConstructor() {
        StateHistory history = new StateHistory();
        
        assertNull(history.getId());
        assertNull(history.getStateMachineName());
        assertNull(history.getBusinessId());
        assertNull(history.getFromState());
        assertNull(history.getToState());
        assertNull(history.getEvent());
        assertNull(history.getOperator());
        assertNull(history.getRemark());
        assertNull(history.getAttributes());
        assertNotNull(history.getCreateTime());
    }

    @Test
    void testConstructorWithParameters() {
        Long businessId = 123L;
        StateHistory history = new StateHistory("order", businessId, "PENDING", "CONFIRMED", "CONFIRM");
        
        assertEquals("order", history.getStateMachineName());
        assertEquals(businessId, history.getBusinessId());
        assertEquals("PENDING", history.getFromState());
        assertEquals("CONFIRMED", history.getToState());
        assertEquals("CONFIRM", history.getEvent());
        assertNotNull(history.getCreateTime());
    }

    @Test
    void testSettersAndGetters() {
        Long businessId = 123L;
        StateHistory history = new StateHistory();
        
        history.setId(1L);
        history.setStateMachineName("order");
        history.setBusinessId(businessId);
        history.setFromState("PENDING");
        history.setToState("CONFIRMED");
        history.setEvent("CONFIRM");
        history.setOperator("user123");
        history.setRemark("手动确认");
        
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("amount", 100.0);
        attributes.put("channel", "WEB");
        history.setAttributes(attributes);
        
        LocalDateTime now = LocalDateTime.now();
        history.setCreateTime(now);
        
        assertEquals(1L, history.getId());
        assertEquals("order", history.getStateMachineName());
        assertEquals(businessId, history.getBusinessId());
        assertEquals("PENDING", history.getFromState());
        assertEquals("CONFIRMED", history.getToState());
        assertEquals("CONFIRM", history.getEvent());
        assertEquals("user123", history.getOperator());
        assertEquals("手动确认", history.getRemark());
        assertEquals(attributes, history.getAttributes());
        assertEquals(now, history.getCreateTime());
    }

    @Test
    void testCreateTime_AutoSet() {
        LocalDateTime before = LocalDateTime.now();
        
        // 等待一小段时间
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        StateHistory history = new StateHistory();
        LocalDateTime after = LocalDateTime.now();
        
        assertTrue(history.getCreateTime().isAfter(before) || history.getCreateTime().equals(before));
        assertTrue(history.getCreateTime().isBefore(after) || history.getCreateTime().equals(after));
    }
}

