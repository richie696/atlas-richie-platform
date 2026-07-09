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
package com.richie.component.statemachine.rule;

import com.richie.component.statemachine.config.properties.RulesEngineConfig;
import com.richie.component.statemachine.context.StateContext;
import com.richie.component.statemachine.model.Transition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StateTransitionRule 单元测试
 *
 * @author richie696
 */
class StateTransitionRuleTest {

    private Transition transition;
    private StateContext context;
    private StateTransitionRule rule;

    @BeforeEach
    void setUp() {
        // 重置配置
        ExpressionConfigHolder.setConfig(null);
        
        // 创建测试转换
        transition = new Transition();
        transition.setName("test_transition");
        transition.setFromState("PENDING");
        transition.setToState("CONFIRMED");
        transition.setEvent("CONFIRM");
        transition.setPriority(1);

        // 创建测试上下文
        context = new StateContext("PENDING", "CONFIRM");
    }

    @Test
    void testWhen_StateAndEventMatch() {
        rule = new StateTransitionRule(transition, context);
        assertTrue(rule.when());
    }

    @Test
    void testWhen_StateMismatch() {
        context.setCurrentState("CONFIRMED");
        rule = new StateTransitionRule(transition, context);
        assertFalse(rule.when());
    }

    @Test
    void testWhen_EventMismatch() {
        context.setEvent("CANCEL");
        rule = new StateTransitionRule(transition, context);
        assertFalse(rule.when());
    }

    @Test
    void testWhen_TransitionEventIsNull() {
        transition.setEvent(null);
        rule = new StateTransitionRule(transition, context);
        assertTrue(rule.when());
    }

    @Test
    void testWhen_WithConditionExpression_True() {
        transition.setCondition("#amount > 0");
        context.setAttribute("amount", 100);
        rule = new StateTransitionRule(transition, context);
        assertTrue(rule.when());
    }

    @Test
    void testWhen_WithConditionExpression_False() {
        transition.setCondition("#amount > 0");
        context.setAttribute("amount", -10);
        rule = new StateTransitionRule(transition, context);
        assertFalse(rule.when());
    }

    @Test
    void testWhen_WithConditionExpression_UsingContext() {
        // SpEL: 使用 #context_attributes 变量访问（map accessor）
        transition.setCondition("#context_attributes['operator'] != null");
        context.setAttribute("operator", "user123");
        rule = new StateTransitionRule(transition, context);
        assertTrue(rule.when());
    }

    @Test
    void testWhen_WithConditionExpression_InvalidExpression() {
        transition.setCondition("invalid expression syntax");
        rule = new StateTransitionRule(transition, context);
        // 表达式执行失败应返回 false
        assertFalse(rule.when());
    }

    @Test
    void testThen_UpdateState() {
        rule = new StateTransitionRule(transition, context);
        rule.then();
        
        assertEquals("CONFIRMED", context.getCurrentState());
        assertEquals("PENDING", context.getPreviousState());
        assertEquals(transition, context.getTransition());
    }

    @Test
    void testThen_WithActionExpression() {
        // SpEL: 测试纯计算表达式（return value 被忽略）
        transition.setAction("#amount * 2");
        context.setAttribute("amount", 100);
        rule = new StateTransitionRule(transition, context);
        rule.then();

        assertEquals("CONFIRMED", context.getCurrentState());
    }

    @Test
    void testThen_WithActionExpression_SettingMultipleAttributes() {
        // SpEL: 测试纯计算表达式
        transition.setAction("#amount + #discount");
        context.setAttribute("amount", 100);
        context.setAttribute("discount", 20);
        rule = new StateTransitionRule(transition, context);
        rule.then();

        assertEquals("CONFIRMED", context.getCurrentState());
    }

    @Test
    void testGetPriority() {
        transition.setPriority(10);
        rule = new StateTransitionRule(transition, context);
        assertEquals(10, rule.getPriority());
    }

    @Test
    void testSecurityCheck_UnsafeExpression() {
        // 配置启用安全检查
        RulesEngineConfig.ExpressionConfig config = new RulesEngineConfig.ExpressionConfig();
        config.setEnableSecurityCheck(true);
        ExpressionConfigHolder.setConfig(config);

        transition.setCondition("System.exit(0)");
        rule = new StateTransitionRule(transition, context);
        assertFalse(rule.when());
    }

    @Test
    void testSecurityCheck_SafeExpression() {
        // 配置启用安全检查
        RulesEngineConfig.ExpressionConfig config = new RulesEngineConfig.ExpressionConfig();
        config.setEnableSecurityCheck(true);
        ExpressionConfigHolder.setConfig(config);

        transition.setCondition("#amount > 0");
        context.setAttribute("amount", 100);
        rule = new StateTransitionRule(transition, context);
        assertTrue(rule.when());
    }

    @Test
    void testSecurityCheck_Disabled() {
        // 配置禁用安全检查
        RulesEngineConfig.ExpressionConfig config = new RulesEngineConfig.ExpressionConfig();
        config.setEnableSecurityCheck(false);
        ExpressionConfigHolder.setConfig(config);

        // 即使是不安全的表达式，如果安全检查被禁用，也应该尝试执行
        transition.setCondition("#amount > 0");
        context.setAttribute("amount", 100);
        rule = new StateTransitionRule(transition, context);
        assertTrue(rule.when());
    }

    @Test
    void evaluate_maliciousSpELExpression_doesNotInvokeMethods() {
        // 验证黑名单阻止 RCE payload（安全机制验证）
        RulesEngineConfig.ExpressionConfig config = new RulesEngineConfig.ExpressionConfig();
        config.setEnableSecurityCheck(true); // 启用黑名单检查
        ExpressionConfigHolder.setConfig(config);

        // Runtime.exec RCE payload - 黑名单包含 java.lang.Runtime，应被拦截
        transition.setCondition("T(java.lang.Runtime).getRuntime().exec('echo pwned')");
        rule = new StateTransitionRule(transition, context);
        // 黑名单检查应在 SpEL 求值前拦截，条件返回 false
        assertFalse(rule.when());
    }

    @Test
    void testConditionWithAttributes() {
        transition.setCondition("#operatorRole == 'ADMIN' || #amount <= 500");
        context.setAttribute("operatorRole", "ADMIN");
        context.setAttribute("amount", 1000);
        rule = new StateTransitionRule(transition, context);
        assertTrue(rule.when());
    }

    @Test
    void evaluate_methodInvocation_throwsSpelException() {
        // SpEL 沙箱阻止方法调用，context.toString() 应抛出异常
        ExpressionConfigHolder.setConfig(null);
        transition.setCondition("context.toString()");
        rule = new StateTransitionRule(transition, context);
        // 沙箱模式下方法调用被禁用，表达式执行失败返回 false
        assertFalse(rule.when());
    }

    @Test
    void evaluate_typeReference_throwsSpelException() {
        // SpEL 沙箱阻止 T(...) 类型引用
        ExpressionConfigHolder.setConfig(null);
        transition.setCondition("T(java.lang.Runtime)");
        rule = new StateTransitionRule(transition, context);
        // 沙箱模式下类型引用被禁用，表达式执行失败返回 false
        assertFalse(rule.when());
    }

    @Test
    void evaluate_constructorCall_throwsSpelException() {
        // SpEL 沙箱阻止构造调用 new ...
        ExpressionConfigHolder.setConfig(null);
        transition.setCondition("new java.util.HashMap()");
        rule = new StateTransitionRule(transition, context);
        // 沙箱模式下构造调用被禁用，表达式执行失败返回 false
        assertFalse(rule.when());
    }

    @Test
    void evaluate_propertyAccess_succeeds() {
        // 属性访问（变量引用）仍然允许，这是沙箱允许的基本操作
        ExpressionConfigHolder.setConfig(null);
        transition.setCondition("#currentState == 'PENDING'");
        rule = new StateTransitionRule(transition, context);
        assertTrue(rule.when());
    }

    @Test
    void testConditionWithComplexExpression() {
        transition.setCondition("(#amount > 0 && #amount < 1000) || #operatorRole == 'ADMIN'");
        context.setAttribute("amount", 500);
        context.setAttribute("operatorRole", "USER");
        rule = new StateTransitionRule(transition, context);
        assertTrue(rule.when());
    }

    @Test
    void testActionWithComplexLogic() {
        // SpEL: 测试纯计算表达式
        transition.setAction("#amount * 1.1 + #amount * 0.1");
        context.setAttribute("amount", 100);
        rule = new StateTransitionRule(transition, context);
        rule.then();

        assertEquals("CONFIRMED", context.getCurrentState());
    }

    @Test
    void testContextIsNotStateContext() {
        Object nonContext = new Object();
        rule = new StateTransitionRule(transition, nonContext);
        assertFalse(rule.when());
    }

    @Test
    void testEvaluateCondition_BlankCondition() {
        ExpressionConfigHolder.setConfig(null);

        transition.setCondition(null);
        rule = new StateTransitionRule(transition, context);
        assertTrue(rule.when());

        transition.setCondition("");
        rule = new StateTransitionRule(transition, context);
        assertTrue(rule.when());

        transition.setCondition("   ");
        rule = new StateTransitionRule(transition, context);
        assertTrue(rule.when());
    }

    @Test
    void testExecuteAction_UnsafeExpression() {
        RulesEngineConfig.ExpressionConfig config = new RulesEngineConfig.ExpressionConfig();
        config.setEnableSecurityCheck(true);
        ExpressionConfigHolder.setConfig(config);

        transition.setAction("Runtime.getRuntime().exec('rm -rf /')");
        rule = new StateTransitionRule(transition, context);
        rule.then();

        assertEquals("CONFIRMED", context.getCurrentState());
        assertNull(context.getAttribute("executed"));
    }

    @Test
    void testExecuteAction_InvalidExpression() {
        RulesEngineConfig.ExpressionConfig config = new RulesEngineConfig.ExpressionConfig();
        config.setEnableSecurityCheck(false);
        ExpressionConfigHolder.setConfig(config);

        transition.setAction("this is :: not :: a valid expression !!!");
        rule = new StateTransitionRule(transition, context);
        rule.then();

        assertEquals("CONFIRMED", context.getCurrentState());
    }
}

