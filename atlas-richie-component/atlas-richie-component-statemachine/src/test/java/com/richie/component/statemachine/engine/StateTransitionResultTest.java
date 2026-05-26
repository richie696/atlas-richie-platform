package com.richie.component.statemachine.engine;

import com.richie.component.statemachine.context.StateContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StateTransitionResult 单元测试
 *
 * @author richie696
 */
class StateTransitionResultTest {

    @Test
    void testSuccess() {
        StateContext context = new StateContext("PENDING", "CONFIRM");
        context.setCurrentState("CONFIRMED");
        context.setPreviousState("PENDING");
        
        StateTransitionResult result = StateTransitionResult.success(context);
        
        assertTrue(result.isSuccess());
        assertNull(result.getErrorMessage());
        assertEquals(context, result.getContext());
        assertEquals("CONFIRMED", result.getCurrentState());
        assertEquals("PENDING", result.getPreviousState());
    }

    @Test
    void testFailure() {
        String errorMessage = "状态转换失败";
        StateTransitionResult result = StateTransitionResult.failure(errorMessage);
        
        assertFalse(result.isSuccess());
        assertEquals(errorMessage, result.getErrorMessage());
        assertNull(result.getContext());
        assertNull(result.getCurrentState());
        assertNull(result.getPreviousState());
    }

    @Test
    void testGetCurrentState_WithContext() {
        StateContext context = new StateContext("PENDING", "CONFIRM");
        context.setCurrentState("CONFIRMED");
        
        StateTransitionResult result = StateTransitionResult.success(context);
        assertEquals("CONFIRMED", result.getCurrentState());
    }

    @Test
    void testGetCurrentState_WithoutContext() {
        StateTransitionResult result = StateTransitionResult.failure("错误");
        assertNull(result.getCurrentState());
    }

    @Test
    void testGetPreviousState_WithContext() {
        StateContext context = new StateContext("PENDING", "CONFIRM");
        context.setCurrentState("CONFIRMED");
        context.setPreviousState("PENDING");
        
        StateTransitionResult result = StateTransitionResult.success(context);
        assertEquals("PENDING", result.getPreviousState());
    }

    @Test
    void testGetPreviousState_WithoutContext() {
        StateTransitionResult result = StateTransitionResult.failure("错误");
        assertNull(result.getPreviousState());
    }

    @Test
    void testSettersAndGetters() {
        StateTransitionResult result = new StateTransitionResult();
        result.setSuccess(true);
        result.setErrorMessage("测试错误");
        
        StateContext context = new StateContext();
        result.setContext(context);
        
        assertTrue(result.isSuccess());
        assertEquals("测试错误", result.getErrorMessage());
        assertEquals(context, result.getContext());
    }
}

