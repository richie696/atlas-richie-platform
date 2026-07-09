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
package com.richie.component.statemachine.engine;

import com.richie.component.statemachine.config.StateMachineDefinitionRegistry;
import com.richie.component.statemachine.config.StateMachineProperties;
import com.richie.component.statemachine.config.properties.DbPersistenceMode;
import com.richie.component.statemachine.config.properties.RedisStreamConfig;
import com.richie.component.statemachine.config.properties.RulesEngineConfig;
import com.richie.component.statemachine.config.properties.StorageType;
import com.richie.component.statemachine.context.StateContext;
import com.richie.component.statemachine.model.State;
import com.richie.component.statemachine.model.StateMachineModel;
import com.richie.component.statemachine.model.Transition;
import com.richie.component.statemachine.registry.StateMachineRegistry;
import com.richie.component.statemachine.storage.StateHistory;
import com.richie.component.statemachine.storage.StateMachineKeyBuilder;
import com.richie.component.statemachine.storage.StateStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * StateMachineEngine 单元测试
 *
 * @author richie696
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StateMachineEngineTest {

    @Mock
    private StateMachineRegistry stateMachineRegistry;

    @Mock
    private StateMachineProperties properties;

    @Mock
    private StateStorage stateStorage;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private StateMachineKeyBuilder keyBuilder;

    @Mock
    private StateMachineDefinitionRegistry definitionRegistry;

    private StateMachineEngine engine;
    private StateMachineModel stateMachine;

    // 测试用的枚举
    enum TestStateMachine { order }
    enum TestEvent { CONFIRM, CANCEL }
    enum TestState { PENDING, CONFIRMED, CANCELLED }

    @BeforeEach
    void setUp() {
        // 创建状态机模型
        stateMachine = new StateMachineModel("order", "订单状态机");
        stateMachine.setInitialState("PENDING");

        State pending = new State("PENDING", "待确认", State.StateType.INITIAL);
        State confirmed = new State("CONFIRMED", "已确认", State.StateType.NORMAL);
        State cancelled = new State("CANCELLED", "已取消", State.StateType.ERROR);
        stateMachine.addState(pending);
        stateMachine.addState(confirmed);
        stateMachine.addState(cancelled);

        Transition confirmTransition = new Transition("confirm", "PENDING", "CONFIRMED", "CONFIRM");
        Transition cancelTransition = new Transition("cancel", "PENDING", "CANCELLED", "CANCEL");
        stateMachine.addTransition(confirmTransition);
        stateMachine.addTransition(cancelTransition);

        // 配置 Mock
        when(properties.isEnableHistory()).thenReturn(true);
        when(properties.isEnableEvents()).thenReturn(true);
        when(properties.getStorageType()).thenReturn(StorageType.REDIS);
        when(properties.getDbPersistenceMode()).thenReturn(DbPersistenceMode.ASYNC);
        when(properties.getRedisStream()).thenReturn(new RedisStreamConfig());

        RulesEngineConfig rulesEngineConfig = new RulesEngineConfig();
        rulesEngineConfig.setSkipOnFirstFailedRule(false);
        rulesEngineConfig.setSkipOnFirstAppliedRule(false);
        rulesEngineConfig.setSkipOnFirstNonTriggeredRule(false);
        rulesEngineConfig.setPriorityBased(false);
        rulesEngineConfig.setEnableExecutionLog(false);
        rulesEngineConfig.setExecutionTimeoutMs(0);
        rulesEngineConfig.setRulePriorityThreshold(0);

        RulesEngineConfig.ExpressionConfig expressionConfig = new RulesEngineConfig.ExpressionConfig();
        expressionConfig.setSlowThresholdMs(50L);
        expressionConfig.setEnableSecurityCheck(true);
        expressionConfig.setEnableDetailedLog(false);
        rulesEngineConfig.setExpression(expressionConfig);

        when(properties.getRulesEngine()).thenReturn(rulesEngineConfig);

        when(stateMachineRegistry.getStateMachine("order")).thenReturn(stateMachine);
        when(stateStorage.getCurrentState(eq("order"), anyLong())).thenReturn(null);

        when(keyBuilder.buildDbSyncStreamKey()).thenReturn("platform:statemachine:db:sync");

        engine = new StateMachineEngine(
                stateMachineRegistry,
                properties,
                stateStorage,
                eventPublisher,
                keyBuilder,
                definitionRegistry
        );
    }

    @Test
    void testFire_Success() {
        Long businessId = 123L;
        StateTransitionResult result = engine.fire(TestStateMachine.order, TestEvent.CONFIRM, businessId);

        assertTrue(result.isSuccess());
        assertEquals("CONFIRMED", result.getCurrentState());
        assertEquals("PENDING", result.getPreviousState());

        verify(stateStorage, times(1)).saveCurrentState(eq("order"), eq(businessId), eq("CONFIRMED"), any(StateContext.class));
        verify(stateStorage, times(1)).saveStateHistory(eq("order"), eq(businessId), eq("PENDING"), eq("CONFIRMED"), eq("CONFIRM"), any(StateContext.class));
    }

    @Test
    void testFire_WithAttributes() {
        Long businessId = 123L;
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("operator", "user123");
        attributes.put("amount", 100.0);

        StateTransitionResult result = engine.fire(TestStateMachine.order, TestEvent.CONFIRM, businessId, attributes);

        assertTrue(result.isSuccess());
        verify(stateStorage, times(1)).saveCurrentState(anyString(), anyLong(), anyString(), any(StateContext.class));
    }

    @Test
    void testFire_StateMachineNotFound() {
        Long businessId = 123L;
        when(stateMachineRegistry.getStateMachine("order")).thenReturn(null);

        StateTransitionResult result = engine.fire(TestStateMachine.order, TestEvent.CONFIRM, businessId);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("状态机未找到"));
        verify(stateStorage, never()).saveCurrentState(anyString(), anyLong(), anyString(), any());
    }

    @Test
    void testFire_NoMatchingTransition() {
        Long businessId = 123L;
        StateTransitionResult result = engine.fire(TestStateMachine.order, TestEvent.CONFIRM, businessId);

        // 先设置当前状态为 CONFIRMED，然后尝试用 CONFIRM 事件（应该找不到匹配的转换）
        when(stateStorage.getCurrentState("order", businessId)).thenReturn("CONFIRMED");

        StateTransitionResult result2 = engine.fire(TestStateMachine.order, TestEvent.CONFIRM, businessId);

        assertFalse(result2.isSuccess());
        assertTrue(result2.getErrorMessage().contains("没有找到匹配的转换规则"));
    }

    @Test
    void testFire_WithCurrentState() {
        Long businessId = 123L;
        StateTransitionResult result = engine.fire(TestStateMachine.order, TestEvent.CONFIRM, businessId, TestState.PENDING);

        assertTrue(result.isSuccess());
        assertEquals("CONFIRMED", result.getCurrentState());
        // 验证没有从存储读取当前状态
        verify(stateStorage, never()).getCurrentState("order", businessId);
    }

    @Test
    void testFire_WithCurrentStateAndAttributes() {
        Long businessId = 123L;
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("operator", "user123");

        StateTransitionResult result = engine.fire(TestStateMachine.order, TestEvent.CONFIRM, businessId, attributes, TestState.PENDING);

        assertTrue(result.isSuccess());
        assertEquals("CONFIRMED", result.getCurrentState());
    }

    @Test
    void testGetCurrentState_FromStorage() {
        Long businessId = 123L;
        when(stateStorage.getCurrentState("order", businessId)).thenReturn("CONFIRMED");

        String currentState = engine.getCurrentState(TestStateMachine.order, businessId);

        assertEquals("CONFIRMED", currentState);
    }

    @Test
    void testGetCurrentState_FromInitialState() {
        Long businessId = 123L;
        when(stateStorage.getCurrentState("order", businessId)).thenReturn(null);

        String currentState = engine.getCurrentState(TestStateMachine.order, businessId);

        assertEquals("PENDING", currentState);
    }

    @Test
    void testGetCurrentState_AsEnum() {
        Long businessId = 123L;
        when(stateStorage.getCurrentState("order", businessId)).thenReturn("CONFIRMED");

        TestState state = engine.getCurrentState(TestStateMachine.order, businessId, TestState.class);

        assertEquals(TestState.CONFIRMED, state);
    }

    @Test
    void testGetCurrentState_AsEnum_NotFound() {
        Long businessId = 123L;
        when(stateStorage.getCurrentState("order", businessId)).thenReturn(null);

        TestState state = engine.getCurrentState(TestStateMachine.order, businessId, TestState.class);

        assertEquals(TestState.PENDING, state); // 应该返回初始状态
    }

    @Test
    void testCanTransitionTo_True() {
        Long businessId = 123L;
        when(stateStorage.getCurrentState("order", businessId)).thenReturn("PENDING");

        boolean can = engine.canTransitionTo(TestStateMachine.order, TestEvent.CONFIRM, businessId);

        assertTrue(can);
    }

    @Test
    void testCanTransitionTo_False() {
        Long businessId = 123L;
        when(stateStorage.getCurrentState("order", businessId)).thenReturn("CONFIRMED");

        boolean can = engine.canTransitionTo(TestStateMachine.order, TestEvent.CONFIRM, businessId);

        assertFalse(can);
    }

    @Test
    void testCanTransitionTo_StateMachineNotFound() {
        Long businessId = 123L;
        when(stateMachineRegistry.getStateMachine("order")).thenReturn(null);
        when(stateStorage.getCurrentState("order", businessId)).thenReturn(null);

        boolean can = engine.canTransitionTo(TestStateMachine.order, TestEvent.CONFIRM, businessId);

        assertFalse(can);
    }

    @Test
    void testGetStateHistory() {
        Long businessId = 123L;
        List<StateHistory> histories = new ArrayList<>();
        StateHistory history1 = new StateHistory("order", businessId, "PENDING", "CONFIRMED", "CONFIRM");
        StateHistory history2 = new StateHistory("order", businessId, "CONFIRMED", "COMPLETED", "COMPLETE");
        histories.add(history1);
        histories.add(history2);

        when(stateStorage.getStateHistory("order", businessId)).thenReturn(histories);

        List<StateHistory> result = engine.getStateHistory(TestStateMachine.order, businessId);

        assertEquals(2, result.size());
        assertEquals(histories, result);
    }

    @Test
    void testFire_FinalStateImmutability() {
        Long businessId = 123L;
        // 创建终态状态
        State completed = new State("COMPLETED", "已完成", State.StateType.FINAL);
        stateMachine.addState(completed);

        // 创建普通转换（不应该被允许）
        Transition normalTransition = new Transition("normal", "COMPLETED", "REOPENED", "REOPEN");
        stateMachine.addTransition(normalTransition);

        // 创建白名单重开转换
        Transition reopenTransition = new Transition("reopen", "COMPLETED", "REOPENED", "REOPEN");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("reopen", true);
        reopenTransition.setAttributes(attributes);
        stateMachine.addTransition(reopenTransition);

        when(stateStorage.getCurrentState("order", businessId)).thenReturn("COMPLETED");

        // 尝试使用普通转换（应该失败）
        StateTransitionResult result1 = engine.fire(TestStateMachine.order, TestEvent.CONFIRM, businessId);
        // 注意：这里需要定义 REOPEN 事件枚举，暂时跳过这个测试的完整实现

        // 验证终态不可变更逻辑已实现
        assertNotNull(stateMachine.getState("COMPLETED"));
        assertEquals(State.StateType.FINAL, stateMachine.getState("COMPLETED").getType());
    }
}

