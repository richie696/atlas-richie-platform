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
package com.richie.component.desensitize.core.registry;

import com.richie.component.desensitize.core.config.DesensitizeProperties;
import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.model.MaskType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Map 键名 → {@link MaskType}，支持全局与分场景覆盖。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
public class SensitiveKeyRegistry {

    /**
     * 依赖组件。
     */
    private final Map<String, MaskType> globalKeys;
    /**
     * 依赖组件。
     */
    private final Map<MaskScene, Map<String, MaskType>> sceneKeys;

    /**
     * 构造敏感键注册表并预计算分场景映射。
     *
     * @param properties 脱敏配置
     */
    public SensitiveKeyRegistry(DesensitizeProperties properties) {
        this.globalKeys = normalize(properties.getSensitiveKeys());
        this.sceneKeys = Map.of(
                MaskScene.API_RESPONSE, merge(globalKeys, properties.getApiResponse().getSensitiveKeys()),
                MaskScene.LOG, merge(globalKeys, properties.getLog().getSensitiveKeys()),
                MaskScene.AUDIT, merge(globalKeys, properties.getLog().getSensitiveKeys()),
                MaskScene.EXCEPTION, merge(globalKeys, Map.of())
        );
    }

    /**
     * 按键名与场景解析脱敏类型。
     *
     * @param key 字段键名
     * @param scene 脱敏场景
     * @return 脱敏类型（若存在）
     */
    public Optional<MaskType> resolve(String key, MaskScene scene) {
        if (key == null) {
            return Optional.empty();
        }
        Map<String, MaskType> map = sceneKeys.getOrDefault(scene, globalKeys);
        return Optional.ofNullable(map.get(key.toLowerCase(Locale.ROOT)));
    }

    /**
     * 返回指定场景下的敏感键映射（只读）。
     *
     * @param scene 脱敏场景
     * @return 场景敏感键映射
     */
    public Map<String, MaskType> keysForScene(MaskScene scene) {
        return Collections.unmodifiableMap(sceneKeys.getOrDefault(scene, globalKeys));
    }

    private static Map<String, MaskType> normalize(Map<String, MaskType> source) {
        Map<String, MaskType> normalized = new HashMap<>();
        if (source != null) {
            source.forEach((k, v) -> normalized.put(k.toLowerCase(Locale.ROOT), v));
        }
        return normalized;
    }

    private static Map<String, MaskType> merge(Map<String, MaskType> base, Map<String, MaskType> override) {
        Map<String, MaskType> merged = new HashMap<>(base);
        if (override != null) {
            override.forEach((k, v) -> merged.put(k.toLowerCase(Locale.ROOT), v));
        }
        return merged;
    }
}
