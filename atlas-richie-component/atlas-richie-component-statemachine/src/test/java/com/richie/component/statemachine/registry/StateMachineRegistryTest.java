package com.richie.component.statemachine.registry;

import com.richie.component.statemachine.model.State;
import com.richie.component.statemachine.model.StateMachineModel;
import com.richie.component.statemachine.model.Transition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StateMachineRegistry 单元测试
 *
 * @author richie696
 */
class StateMachineRegistryTest {

    private StateMachineRegistry registry;
    private StateMachineModel stateMachine;

    @BeforeEach
    void setUp() {
        registry = new StateMachineRegistry();
        
        // 创建测试状态机
        stateMachine = new StateMachineModel("order", "订单状态机");
        stateMachine.setInitialState("PENDING");
        
        State pending = new State("PENDING", "待确认", State.StateType.INITIAL);
        State confirmed = new State("CONFIRMED", "已确认", State.StateType.NORMAL);
        stateMachine.addState(pending);
        stateMachine.addState(confirmed);
        
        Transition transition = new Transition("confirm", "PENDING", "CONFIRMED", "CONFIRM");
        stateMachine.addTransition(transition);
    }

    @Test
    void testRegister() {
        registry.register(stateMachine);
        
        assertTrue(registry.contains("order"));
        StateMachineModel retrieved = registry.getStateMachine("order");
        assertNotNull(retrieved);
        assertEquals("order", retrieved.getName());
    }

    @Test
    void testRegister_NullStateMachine() {
        assertThrows(IllegalArgumentException.class, () -> {
            registry.register(null);
        });
    }

    @Test
    void testRegister_StateMachineWithNullName() {
        StateMachineModel invalid = new StateMachineModel();
        assertThrows(IllegalArgumentException.class, () -> {
            registry.register(invalid);
        });
    }

    @Test
    void testGetStateMachine_NotExists() {
        assertNull(registry.getStateMachine("nonExistent"));
    }

    @Test
    void testRemove() {
        registry.register(stateMachine);
        
        StateMachineModel removed = registry.remove("order");
        assertNotNull(removed);
        assertEquals("order", removed.getName());
        assertFalse(registry.contains("order"));
    }

    @Test
    void testRemove_NotExists() {
        StateMachineModel removed = registry.remove("nonExistent");
        assertNull(removed);
    }

    @Test
    void testContains() {
        assertFalse(registry.contains("order"));
        registry.register(stateMachine);
        assertTrue(registry.contains("order"));
    }

    @Test
    void testGetStateMachineNames() {
        registry.register(stateMachine);
        
        StateMachineModel payment = new StateMachineModel("payment", "支付状态机");
        registry.register(payment);
        
        var names = registry.getStateMachineNames();
        assertEquals(2, names.size());
        assertTrue(names.contains("order"));
        assertTrue(names.contains("payment"));
    }

    @Test
    void testGetAllStateMachines() {
        registry.register(stateMachine);
        
        StateMachineModel payment = new StateMachineModel("payment", "支付状态机");
        registry.register(payment);
        
        var all = registry.getAllStateMachines();
        assertEquals(2, all.size());
        assertTrue(all.containsKey("order"));
        assertTrue(all.containsKey("payment"));
        
        // 验证返回的是副本，修改不影响原注册表
        all.clear();
        assertEquals(2, registry.getAllStateMachines().size());
    }

    @Test
    void testClear() {
        registry.register(stateMachine);
        registry.register(new StateMachineModel("payment", "支付状态机"));
        
        assertEquals(2, registry.getStateMachineNames().size());
        
        registry.clear();
        
        assertEquals(0, registry.getStateMachineNames().size());
        assertFalse(registry.contains("order"));
        assertFalse(registry.contains("payment"));
    }

    @Test
    void testMultipleRegistrations_SameName() {
        registry.register(stateMachine);
        
        StateMachineModel newOrder = new StateMachineModel("order", "新订单状态机");
        registry.register(newOrder);
        
        StateMachineModel retrieved = registry.getStateMachine("order");
        assertEquals("新订单状态机", retrieved.getDescription());
    }
}

