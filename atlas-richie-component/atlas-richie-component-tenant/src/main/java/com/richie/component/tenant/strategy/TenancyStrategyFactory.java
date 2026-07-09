/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.tenant.strategy;

import com.richie.component.tenant.model.IsolationMode;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 策略工厂：按隔离模式返回对应的策略实现。
 *
 * <p>启动时自动扫描所有 {@link TenancyStrategy} Bean，构建
 * {@code IsolationMode → TenancyStrategy} 映射表。</p>
 *
 * @author richie696
 * @since 2.0
 */
public class TenancyStrategyFactory {

    private final Map<IsolationMode, TenancyStrategy> strategyMap;

    /**
     * 构造策略工厂,自动扫描所有 {@link TenancyStrategy} Bean,
     * 按 {@link TenancyStrategy#supports(IsolationMode)} 构建策略路由表。
     *
     * <p>每种 {@link IsolationMode} 必须有恰好一个 Bean {@code supports()} 它,
     * 否则构造时抛 {@link IllegalArgumentException}（fail-fast）。</p>
     *
     * @param strategies Spring 注入的所有 {@link TenancyStrategy} Bean
     * @throws IllegalArgumentException 某 {@link IsolationMode} 无对应策略时
     */
    /**
     * 构造策略工厂。扫描注入的 5 个 {@link TenancyStrategy} Bean,
     * 按 {@link TenancyStrategy#supports(IsolationMode)} 构建
     * {@code IsolationMode → TenancyStrategy} 映射。
     *
     * <p>5 种 {@link IsolationMode} 必须全部找到对应策略(否则启动失败),
     * 例如忘记注册 {@code SchemaStrategy} Bean 时启动即报错,
     * 比运行时 SQL 报错更早暴露配置错误。</p>
     *
     * @param strategies 所有策略实现 Bean(Spring 自动注入 List)
     * @throws IllegalArgumentException 任意 IsolationMode 找不到对应策略时
     */
    public TenancyStrategyFactory(List<TenancyStrategy> strategies) {
        this.strategyMap = Arrays.stream(IsolationMode.values())
            .collect(Collectors.toMap(
                Function.identity(),
                mode -> strategies.stream()
                    .filter(s -> s.supports(mode))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                        "No strategy found for mode: " + mode)),
                (existing, replacement) -> existing,
                () -> new EnumMap<>(IsolationMode.class)
            ));
    }

    /**
     * 获取指定隔离模式的策略实现。
     *
     * @param mode 隔离模式
     * @return 策略实例
     * @throws IllegalArgumentException 无对应策略时
     */
    public TenancyStrategy getStrategy(IsolationMode mode) {
        TenancyStrategy strategy = strategyMap.get(mode);
        if (strategy == null) {
            throw new IllegalArgumentException("No strategy for mode: " + mode);
        }
        return strategy;
    }
}
