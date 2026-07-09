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
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 表达式配置持有者
 * <p>
 * 静态配置持有者，用于在 StateTransitionRule（record 类型）中访问表达式配置。
 * 因为 Java record 无法直接使用 Spring 依赖注入，所以使用静态方式持有配置。
 * 
 *
 * @author richie696
 * @since 1.0.0
 */
public class ExpressionConfigHolder {

    /**
     * 表达式配置对象（静态持有）
     */
    @Getter
    @Setter
    private static RulesEngineConfig.ExpressionConfig config;

    /**
     * 获取慢查询阈值
     * <p>
     * 返回表达式执行时间的慢查询阈值（毫秒）。
     * 如果配置为 null，则返回默认值 50 毫秒。
     * 
     *
     * @return 慢查询阈值（毫秒）
     */
    public static long getSlowThresholdMs() {
        return config != null ? config.getSlowThresholdMs() : 50L;
    }

    /**
     * 是否启用安全检查
     * <p>
     * 返回是否启用表达式安全检查。
     * 如果配置为 null，则默认启用安全检查。
     * 
     *
     * @return true 表示启用安全检查，false 表示不启用
     */
    public static boolean isSecurityCheckEnabled() {
        return config == null || config.isEnableSecurityCheck();
    }

    /**
     * 是否启用详细日志
     * <p>
     * 返回是否启用表达式执行的详细日志记录。
     * 如果配置为 null 或未启用，则返回 false。
     * 
     *
     * @return true 表示启用详细日志，false 表示不启用
     */
    public static boolean isDetailedLogEnabled() {
        return config != null && config.isEnableDetailedLog();
    }

    /**
     * 获取安全黑名单
     * <p>
     * 返回表达式安全黑名单集合，包含默认黑名单和自定义黑名单。
     * 黑名单用于防止执行危险的方法或类（如 Runtime.exec、System.exit 等）。
     * 
     *
     * @return 安全黑名单集合（包含默认和自定义黑名单）
     */
    public static Set<String> getSecurityBlacklist() {
        Set<String> blacklist = new HashSet<>();
        
        // 默认黑名单
        blacklist.addAll(Arrays.asList(
            "java.lang.Runtime",
            "ProcessBuilder",
            "System.exit",
            "Class.forName",
            "ClassLoader",
            "java.lang.reflect",
            "new java.io",
            "new java.net",
            "java.nio.file",
            "Thread.",
            "Executors",
            "System.setProperty",
            "getClass()"
        ));

        // 从配置中添加自定义黑名单
        if (config != null && config.getSecurityBlacklist() != null && !config.getSecurityBlacklist().isBlank()) {
            String[] custom = config.getSecurityBlacklist().split(",");
            for (String item : custom) {
                String trimmed = item.trim();
                if (!trimmed.isEmpty()) {
                    blacklist.add(trimmed);
                }
            }
        }

        return blacklist;
    }
}

