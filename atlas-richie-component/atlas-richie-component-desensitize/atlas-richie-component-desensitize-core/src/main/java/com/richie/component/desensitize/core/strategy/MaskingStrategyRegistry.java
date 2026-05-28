package com.richie.component.desensitize.core.strategy;

import com.richie.component.desensitize.core.model.MaskType;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 按 {@link MaskType} 解析 {@link MaskingStrategy}。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
public class MaskingStrategyRegistry {

    /**
     * 依赖组件。
     */
    private final Map<MaskType, MaskingStrategy> strategies;

    /**
     * 基于策略列表按类型建立索引。
     *
     * @param strategyList 策略列表
     */
    public MaskingStrategyRegistry(List<MaskingStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .flatMap(s -> {
                    MaskingStrategy strategy = s;
                    return java.util.Arrays.stream(MaskType.values())
                            .filter(strategy::supports)
                            .map(t -> Map.entry(t, strategy));
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
    }

    /**
     * 获取指定类型的脱敏策略，不存在时抛异常。
     *
     * @param type 脱敏类型
     * @return 对应策略
     */
    public MaskingStrategy require(MaskType type) {
        MaskingStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new IllegalStateException("No MaskingStrategy registered for type: " + type);
        }
        return strategy;
    }
}
