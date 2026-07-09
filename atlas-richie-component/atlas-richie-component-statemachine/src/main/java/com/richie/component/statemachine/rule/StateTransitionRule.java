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
package com.richie.component.statemachine.rule;

import com.richie.component.statemachine.context.StateContext;
import com.richie.component.statemachine.model.Transition;
import lombok.extern.slf4j.Slf4j;
import org.jeasy.rules.annotation.Action;
import org.jeasy.rules.annotation.Condition;
import org.jeasy.rules.annotation.Priority;
import org.jeasy.rules.annotation.Rule;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import java.util.Set;

/**
 * 状态转换规则
 * <p>
 * Easy Rules 规则实现，用于执行状态转换的条件判断和动作执行。
 * 支持 Spring SpEL 表达式进行条件评估和动作执行，包含表达式安全检查和性能监控。
 * <p>
 * 安全说明：使用 SpEL 沙箱模式（SimpleEvaluationContext.forReadOnlyDataBinding）禁用方法调用、
 * 构造调用、类型引用（T(...)）和 Bean 引用；配合 {@link #isSafeExpression(String)} 黑名单检查
 * 实现深度防御，阻止危险方法调用（如 Runtime.exec、System.exit 等）。
 *
 * @param transition 转换定义对象，包含转换的源状态、目标状态、事件、条件、动作等信息
 * @param context    状态上下文对象，包含当前状态、事件、属性等信息
 * @author richie696
 * @since 1.0.0
 */
@Rule(name = "StateTransitionRule", description = "状态转换规则")
@Slf4j
public record StateTransitionRule(Transition transition, Object context) {

    /**
     * SpEL 表达式解析器（线程安全，可复用）
     */
    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();

    /**
     * 条件判断方法
     * <p>
     * Easy Rules 的条件方法，用于判断该规则是否应该被触发。
     * 检查当前状态和事件是否匹配转换规则，如果设置了条件表达式，还会评估条件。
     *
     *
     * @return true 表示条件满足，规则应该被触发；false 表示条件不满足
     */
    @Condition
    public boolean when() {
        // 检查当前状态是否匹配
        if (context instanceof StateContext stateContext) {
            String currentState = stateContext.getCurrentState();
            String event = stateContext.getEvent();

            // 检查状态和事件是否匹配
            boolean stateMatch = transition.getFromState() == null || transition.getFromState().equals(currentState);
            boolean eventMatch = transition.getEvent() == null ||
                    transition.getEvent().equals(event);

            // 如果有条件表达式，需要额外检查
            if (stateMatch && eventMatch && transition.getCondition() != null) {
                return evaluateCondition(transition.getCondition(), context);
            }

            return stateMatch && eventMatch;
        }
        return false;
    }

    /**
     * 动作执行方法
     * <p>
     * Easy Rules 的动作方法，当条件满足时执行。
     * 执行转换动作表达式（如果设置了），并更新状态上下文。
     *
     */
    @Action
    public void then() {
        if (context instanceof StateContext stateContext) {
            // 执行转换动作
            if (transition.getAction() != null) {
                executeAction(transition.getAction(), context);
            }

            // 更新状态
            stateContext.setCurrentState(transition.getToState());
            stateContext.setPreviousState(transition.getFromState());
            stateContext.setTransition(transition);
        }
    }

    /**
     * 获取规则优先级
     * <p>
     * Easy Rules 的优先级方法，返回转换规则的优先级。
     * 数字越大优先级越高。
     *
     *
     * @return 规则优先级
     */
    @Priority
    public int getPriority() {
        return transition.getPriority();
    }

    /**
     * 评估条件表达式
     * <p>
     * 使用 Spring SpEL 引擎评估条件表达式，支持表达式安全检查和执行时间监控。
     * 使用 SpEL 沙箱模式（SimpleEvaluationContext.forReadOnlyDataBinding）禁用方法调用、构造调用、
     * 类型引用（T(...)）和 Bean 引用，配合 isSafeExpression 黑名单检查实现深度防御。
     *
     * @param condition 条件表达式（SpEL）
     * @param context    状态上下文对象
     * @return true 表示条件满足，false 表示条件不满足或执行失败
     */
    private boolean evaluateCondition(String condition, Object context) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        if (ExpressionConfigHolder.isSecurityCheckEnabled() && !isSafeExpression(condition)) {
            log.warn("拒绝不安全的条件表达式: {}", abbreviate(condition));
            return false;
        }
        long start = System.nanoTime();
        try {
            java.util.Map<String, Object> vars = buildVariables(context);
            // SimpleEvaluationContext 是 Spring 官方沙箱模式,禁用方法调用/构造调用/类型引用/Bean 引用
            SimpleEvaluationContext evalContext = SimpleEvaluationContext.forReadOnlyDataBinding().build();
            evalContext.setVariable("context", vars.get("context"));
            evalContext.setVariable("currentState", vars.get("currentState"));
            evalContext.setVariable("previousState", vars.get("previousState"));
            evalContext.setVariable("event", vars.get("event"));
            evalContext.setVariable("transition", vars.get("transition"));
            evalContext.setVariable("context_attributes", vars.get("attributes"));
            // 将 attributes 展开为独立变量，使 #amount 直接可用
            if (vars.get("attributes") instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> attrs = (java.util.Map<String, Object>) vars.get("attributes");
                if (attrs != null) {
                    attrs.forEach(evalContext::setVariable);
                }
            }
            Expression expr = SPEL_PARSER.parseExpression(condition);
            Object result = expr.getValue(evalContext, Object.class);
            return result instanceof Boolean ? (Boolean) result : result != null;
        } catch (Exception e) {
            log.warn("条件表达式执行失败: {} - {}", abbreviate(condition), e.getMessage());
            return false;
        } finally {
            long costMs = (System.nanoTime() - start) / 1_000_000L;
            long threshold = ExpressionConfigHolder.getSlowThresholdMs();
            if (costMs > threshold) {
                log.warn("条件表达式执行过慢: {} ms (阈值: {} ms), expr={}", costMs, threshold, abbreviate(condition));
            } else if (ExpressionConfigHolder.isDetailedLogEnabled() && log.isDebugEnabled()) {
                log.debug("条件表达式耗时: {} ms, expr={}", costMs, abbreviate(condition));
            }
        }
    }

    /**
     * 执行动作表达式
     * <p>
     * 使用 Spring SpEL 引擎执行动作表达式，支持表达式安全检查和执行时间监控。
     * 使用 SpEL 沙箱模式（SimpleEvaluationContext.forReadOnlyDataBinding）禁用方法调用、构造调用、
     * 类型引用（T(...)）和 Bean 引用，配合 isSafeExpression 黑名单检查实现深度防御。
     *
     * @param action  动作表达式（SpEL）
     * @param context 状态上下文对象
     */
    private void executeAction(String action, Object context) {
        if (action == null || action.isBlank()) {
            return;
        }
        if (ExpressionConfigHolder.isSecurityCheckEnabled() && !isSafeExpression(action)) {
            log.warn("拒绝不安全的动作表达式: {}", abbreviate(action));
            return;
        }
        long start = System.nanoTime();
        try {
            java.util.Map<String, Object> vars = buildVariables(context);
            // SimpleEvaluationContext 是 Spring 官方沙箱模式,禁用方法调用/构造调用/类型引用/Bean 引用
            SimpleEvaluationContext evalContext = SimpleEvaluationContext.forReadOnlyDataBinding().build();
            evalContext.setVariable("context", vars.get("context"));
            evalContext.setVariable("currentState", vars.get("currentState"));
            evalContext.setVariable("previousState", vars.get("previousState"));
            evalContext.setVariable("event", vars.get("event"));
            evalContext.setVariable("transition", vars.get("transition"));
            evalContext.setVariable("context_attributes", vars.get("attributes"));
            if (vars.get("attributes") instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> attrs = (java.util.Map<String, Object>) vars.get("attributes");
                if (attrs != null) {
                    attrs.forEach(evalContext::setVariable);
                }
            }
            Expression expr = SPEL_PARSER.parseExpression(action);
            expr.getValue(evalContext);
        } catch (Exception e) {
            log.warn("动作表达式执行失败: {} - {}", abbreviate(action), e.getMessage());
        } finally {
            long costMs = (System.nanoTime() - start) / 1_000_000L;
            long threshold = ExpressionConfigHolder.getSlowThresholdMs();
            if (costMs > threshold) {
                log.warn("动作表达式执行过慢: {} ms (阈值: {} ms), expr={}", costMs, threshold, abbreviate(action));
            } else if (ExpressionConfigHolder.isDetailedLogEnabled() && log.isDebugEnabled()) {
                log.debug("动作表达式耗时: {} ms, expr={}", costMs, abbreviate(action));
            }
        }
    }

    /**
     * 构建 SpEL 表达式变量
     * <p>
     * 将状态上下文和转换对象转换为 SpEL 表达式可用的变量映射。
     * 表达式可以通过变量名直接访问上下文属性和转换信息。
     *
     * @param ctx 状态上下文对象
     * @return 变量映射表，包含 context、currentState、previousState、event、attributes、transition 等
     */
    private java.util.Map<String, Object> buildVariables(Object ctx) {
        java.util.Map<String, Object> vars = new java.util.HashMap<>();
        if (ctx instanceof StateContext sc) {
            vars.put("context", sc);
            vars.put("currentState", sc.getCurrentState());
            vars.put("previousState", sc.getPreviousState());
            vars.put("event", sc.getEvent());
            vars.put("attributes", sc.getAttributes());
            vars.putAll(sc.getAttributes());
        }
        vars.put("transition", transition);
        return vars;
    }

    /**
     * 检查表达式是否安全
     * <p>
     * 检查表达式是否包含黑名单中的危险方法或类，防止执行不安全的代码。
     * 黑名单是主要的安全隔离手段，阻止明显的危险模式（如 Runtime.exec、System.exit 等）。
     *
     * @param expr 表达式字符串
     * @return true 表示表达式安全，false 表示表达式不安全
     */
    private boolean isSafeExpression(String expr) {
        String s = expr;
        Set<String> blacklist = ExpressionConfigHolder.getSecurityBlacklist();
        for (String bad : blacklist) {
            if (s.contains(bad)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 缩写表达式字符串
     * <p>
     * 用于日志输出，如果表达式过长则截断并添加省略号。
     *
     *
     * @param expr 表达式字符串
     * @return 缩写后的表达式字符串（如果长度超过 120 字符则截断）
     */
    private String abbreviate(String expr) {
        if (expr == null) return "";
        return expr.length() > 120 ? expr.substring(0, 117) + "..." : expr;
    }
}
