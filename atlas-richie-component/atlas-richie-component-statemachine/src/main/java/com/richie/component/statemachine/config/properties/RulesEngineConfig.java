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
package com.richie.component.statemachine.config.properties;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 规则引擎配置
 * <p>
 * 配置 Easy Rules 引擎的执行策略和参数。
 * 
 * <p>
 * 注意：此类使用 Lombok {@code @Data} 注解，构造函数由 Lombok 自动生成。
 * 
 */
@Data
@ConfigurationProperties(prefix = "platform.component.statemachine.rules-engine")
public class RulesEngineConfig {
    /**
     * 是否跳过失败的规则
     * 当第一个规则执行失败时，是否停止执行后续规则
     */
    private boolean skipOnFirstFailedRule = false;

    /**
     * 是否跳过第一个应用的规则
     * 当第一个规则被应用后，是否停止执行后续规则
     */
    private boolean skipOnFirstAppliedRule = false;

    /**
     * 是否跳过第一个非触发的规则
     * 当第一个规则未触发时，是否停止执行后续规则
     */
    private boolean skipOnFirstNonTriggeredRule = false;

    /**
     * 规则优先级阈值
     * 只有优先级大于等于此阈值的规则才会被执行
     * 默认值 0 表示执行所有规则
     */
    private int rulePriorityThreshold = 0;

    /**
     * 规则优先级策略
     * true: 按优先级从高到低执行（优先级数字越大，优先级越高）
     * false: 按注册顺序执行
     */
    private boolean priorityBased = true;

    /**
     * 是否启用规则执行日志
     * true: 记录规则执行详情（debug级别）
     * false: 仅记录错误
     */
    private boolean enableExecutionLog = false;

    /**
     * 规则执行超时时间（毫秒）
     * 0 表示不设置超时
     * 注意：Easy Rules 本身不支持超时，此配置用于监控和告警
     */
    private long executionTimeoutMs = 0L;

    /**
     * 表达式执行配置
     */
    private ExpressionConfig expression = new ExpressionConfig();



    /**
     * 表达式执行配置
     * <p>
     * 配置 Spring SpEL 表达式的执行行为，包括安全检查、慢查询监控、详细日志等。
     *
     * <p>
     * 注意：此类使用 Lombok {@code @Data} 注解，构造函数由 Lombok 自动生成。
     *
     */
    @Data
    public static class ExpressionConfig {
        /**
         * 表达式执行超时阈值（毫秒）
         * 超过此阈值的表达式执行会被记录为慢查询
         */
        private long slowThresholdMs = 50L;

        /**
         * 是否启用表达式安全检查
         * true: 启用黑名单检查，拒绝不安全的表达式
         * false: 仅记录警告，不拒绝执行
         */
        private boolean enableSecurityCheck = true;

        /**
         * 表达式安全黑名单（逗号分隔）
         * 如果为空，使用默认黑名单
         */
        private String securityBlacklist = "";

        /**
         * 是否记录表达式执行详情
         * true: 记录所有表达式的执行时间和结果（debug级别）
         * false: 仅记录慢查询和错误
         */
        private boolean enableDetailedLog = false;
    }
}
