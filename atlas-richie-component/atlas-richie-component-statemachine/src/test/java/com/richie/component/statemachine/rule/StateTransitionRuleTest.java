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
        transition.setCondition("amount > 0");
        context.setAttribute("amount", 100);
        rule = new StateTransitionRule(transition, context);
        assertTrue(rule.when());
    }

    @Test
    void testWhen_WithConditionExpression_False() {
        transition.setCondition("amount > 0");
        context.setAttribute("amount", -10);
        rule = new StateTransitionRule(transition, context);
        assertFalse(rule.when());
    }

    @Test
    void testWhen_WithConditionExpression_UsingContext() {
        transition.setCondition("context.getAttribute('operator') != null");
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
        transition.setAction("context.setAttribute('completedAt', java.time.LocalDateTime.now())");
        rule = new StateTransitionRule(transition, context);
        rule.then();
        
        assertEquals("CONFIRMED", context.getCurrentState());
        assertNotNull(context.getAttribute("completedAt"));
    }

    @Test
    void testThen_WithActionExpression_SettingMultipleAttributes() {
        transition.setAction("context.setAttribute('operator', 'admin'); context.setAttribute('reason', 'manual')");
        rule = new StateTransitionRule(transition, context);
        rule.then();
        
        assertEquals("admin", context.getAttribute("operator"));
        assertEquals("manual", context.getAttribute("reason"));
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

        transition.setCondition("amount > 0");
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
        transition.setCondition("amount > 0");
        context.setAttribute("amount", 100);
        rule = new StateTransitionRule(transition, context);
        assertTrue(rule.when());
    }

    @Test
    void testConditionWithAttributes() {
        transition.setCondition("operatorRole == 'ADMIN' || amount <= 500");
        context.setAttribute("operatorRole", "ADMIN");
        context.setAttribute("amount", 1000);
        rule = new StateTransitionRule(transition, context);
        assertTrue(rule.when());
    }

    @Test
    void testConditionWithComplexExpression() {
        transition.setCondition("(amount > 0 && amount < 1000) || operatorRole == 'ADMIN'");
        context.setAttribute("amount", 500);
        context.setAttribute("operatorRole", "USER");
        rule = new StateTransitionRule(transition, context);
        assertTrue(rule.when());
    }

    @Test
    void testActionWithComplexLogic() {
        transition.setAction("context.setAttribute('total', amount * 1.1); context.setAttribute('tax', amount * 0.1)");
        context.setAttribute("amount", 100);
        rule = new StateTransitionRule(transition, context);
        rule.then();
        
        assertEquals(110.0, (Double) context.getAttribute("total"), 0.01);
        assertEquals(10.0, (Double) context.getAttribute("tax"), 0.01);
    }

    @Test
    void testContextIsNotStateContext() {
        Object nonContext = new Object();
        rule = new StateTransitionRule(transition, nonContext);
        assertFalse(rule.when());
    }
}

