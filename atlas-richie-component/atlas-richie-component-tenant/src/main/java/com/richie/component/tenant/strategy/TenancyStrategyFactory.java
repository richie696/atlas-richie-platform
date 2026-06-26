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
