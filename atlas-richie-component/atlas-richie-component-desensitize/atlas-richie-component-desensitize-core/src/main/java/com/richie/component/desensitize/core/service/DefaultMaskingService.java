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
package com.richie.component.desensitize.core.service;

import com.richie.component.desensitize.core.config.DesensitizeProperties;
import com.richie.component.desensitize.core.model.MaskContext;
import com.richie.component.desensitize.core.model.MaskRule;
import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.model.MaskType;
import com.richie.component.desensitize.core.permission.MaskPermissionEvaluator;
import com.richie.component.desensitize.core.registry.MaskRuleRegistry;
import com.richie.component.desensitize.core.registry.SensitiveKeyRegistry;
import com.richie.component.desensitize.core.strategy.MaskingStrategy;
import com.richie.component.desensitize.core.strategy.MaskingStrategyRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@link MaskingService} 默认实现。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
public class DefaultMaskingService implements MaskingService {

    /**
     * 依赖组件。
     */
    private final DesensitizeProperties properties;
    /**
     * 核心依赖组件。
     */
    private final MaskingStrategyRegistry strategyRegistry;
    /**
     * 核心依赖组件。
     */
    private final MaskRuleRegistry ruleRegistry;
    /**
     * 核心依赖组件。
     */
    private final SensitiveKeyRegistry sensitiveKeyRegistry;
    /**
     * 核心依赖组件。
     */
    private final MaskPermissionEvaluator permissionEvaluator;

    /**
     * 构造默认脱敏服务。
     *
     * @param properties 脱敏配置
     * @param strategyRegistry 策略注册表
     * @param ruleRegistry 规则注册表
     * @param sensitiveKeyRegistry 敏感键注册表
     * @param permissionEvaluator 权限评估器
     */
    public DefaultMaskingService(
            DesensitizeProperties properties,
            MaskingStrategyRegistry strategyRegistry,
            MaskRuleRegistry ruleRegistry,
            SensitiveKeyRegistry sensitiveKeyRegistry,
            MaskPermissionEvaluator permissionEvaluator) {
        this.properties = properties;
        this.strategyRegistry = strategyRegistry;
        this.ruleRegistry = ruleRegistry;
        this.sensitiveKeyRegistry = sensitiveKeyRegistry;
        this.permissionEvaluator = permissionEvaluator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String mask(String raw, MaskType type) {
        return mask(raw, MaskContext.of(MaskScene.LOG), type);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String mask(String raw, MaskType type, MaskScene scene) {
        return mask(raw, MaskContext.of(scene), type);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String mask(String raw, MaskContext context, MaskType type) {
        if (raw == null) {
            return null;
        }
        // 全局开关或场景开关关闭时直接返回原文。
        if (!properties.isEnabled() || !properties.isSceneEnabled(context.scene())) {
            return raw;
        }
        // 权限允许明文时不执行脱敏。
        if (!permissionEvaluator.shouldMask(context)) {
            return raw;
        }
        MaskRule rule = ruleRegistry.toRule(type);
        MaskingStrategy strategy = strategyRegistry.require(type);
        return strategy.mask(raw, rule);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    /**
     * maskMap。
     * @param Map<String 参数
     * @param source 参数
     * @return 处理结果
     */
    public Map<String, Object> maskMap(Map<String, ?> source) {
        return maskMap(source, MaskScene.LOG);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    /**
     * maskMap。
     * @param Map<String 参数
     * @param source 参数
     * @param scene 参数
     * @return 处理结果
     */
    public Map<String, Object> maskMap(Map<String, ?> source, MaskScene scene) {
        if (source == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String str) {
                Optional<MaskType> type = sensitiveKeyRegistry.resolve(key, scene);
                result.put(key, type.map(t -> mask(str, MaskContext.of(scene, key, null), t)).orElse(str));
            } else if (value instanceof Map<?, ?> nested) {
                // 递归处理嵌套 Map。
                @SuppressWarnings("unchecked")
                Map<String, ?> nestedMap = (Map<String, ?>) nested;
                result.put(key, maskMap(nestedMap, scene));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }
}
