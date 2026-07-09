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
package com.richie.component.desensitize.core.serializer;

import com.richie.component.desensitize.core.annotation.Sensitive;
import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.model.MaskType;
import com.richie.component.desensitize.core.registry.MaskRuleRegistry;
import com.richie.component.desensitize.core.service.MaskingService;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;

/**
 * 将对象 / Map 转为日志安全 JSON 字符串（不依赖 Jackson）。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
public class SafeLogSerializer {

    /**
     * 常量定义。
     */
    private static final int MAX_DEPTH = 8;

    /**
     * 核心依赖组件。
     */
    private final MaskingService maskingService;
    /**
     * 核心依赖组件。
     */
    private final MaskRuleRegistry ruleRegistry;

    /**
     * 构造日志安全序列化器。
     *
     * @param maskingService 脱敏服务
     * @param ruleRegistry 字段规则注册表
     */
    public SafeLogSerializer(MaskingService maskingService, MaskRuleRegistry ruleRegistry) {
        this.maskingService = maskingService;
        this.ruleRegistry = ruleRegistry;
    }

    /**
     * 将任意对象序列化为日志安全 JSON。
     *
     * @param value 原始对象
     * @return 安全 JSON
     */
    public String toSafeJson(Object value) {
        return toSafeJson(value, MaskScene.LOG, 0);
    }

    /**
     * 将 Map 序列化为日志安全字符串。
     *
     * @param map 原始 Map
     * @return 安全字符串
     */
    public String toSafeString(Map<String, ?> map) {
        if (map == null) {
            return "null";
        }
        Map<String, Object> masked = maskingService.maskMap(map, MaskScene.LOG);
        return writeMap(masked, 0);
    }

    private String toSafeJson(Object value, MaskScene scene, int depth) {
        if (value == null) {
            return "null";
        }
        if (depth > MAX_DEPTH) {
            return quote("[max-depth]");
        }
        if (value instanceof String str) {
            return quote(str);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, ?> stringMap = (Map<String, ?>) map;
            return writeMap(maskingService.maskMap(stringMap, scene), depth);
        }
        if (value instanceof Collection<?> collection) {
            return writeArray(collection.toArray(), scene, depth);
        }
        if (value.getClass().isArray()) {
            return writeArray(value, scene, depth);
        }
        // 非基础类型按 Bean 字段递归序列化。
        return writeBean(value, scene, depth);
    }

    private String writeBean(Object bean, MaskScene scene, int depth) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        Class<?> type = bean.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Object fieldValue;
                try {
                    fieldValue = field.get(bean);
                } catch (IllegalAccessException ex) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(quote(field.getName())).append(':');
                sb.append(serializeFieldValue(type, field, fieldValue, scene, depth + 1));
            }
            type = type.getSuperclass();
        }
        sb.append('}');
        return sb.toString();
    }

    private String serializeFieldValue(Class<?> declaringClass, Field field, Object fieldValue, MaskScene scene, int depth) {
        if (fieldValue instanceof String str) {
            MaskType type = resolveFieldMaskType(declaringClass, field, scene);
            if (type != null) {
                return quote(maskingService.mask(str, com.richie.component.desensitize.core.model.MaskContext.of(scene, field.getName(), declaringClass), type));
            }
            return quote(str);
        }
        return toSafeJson(fieldValue, scene, depth);
    }

    private MaskType resolveFieldMaskType(Class<?> declaringClass, Field field, MaskScene scene) {
        Sensitive sensitive = field.getAnnotation(Sensitive.class);
        if (sensitive != null) {
            for (MaskScene s : sensitive.scenes()) {
                if (s == scene) {
                    return sensitive.type();
                }
            }
        }
        return ruleRegistry.resolveFieldType(declaringClass, field.getName(), scene).orElse(null);
    }

    private String writeMap(Map<String, ?> map, int depth) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(quote(entry.getKey())).append(':');
            sb.append(toSafeJson(entry.getValue(), MaskScene.LOG, depth + 1));
        }
        sb.append('}');
        return sb.toString();
    }

    private String writeArray(Object array, MaskScene scene, int depth) {
        int len = array instanceof Object[] objects ? objects.length : Array.getLength(array);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < len; i++) {
            if (i > 0) {
                sb.append(',');
            }
            Object element = array instanceof Object[] objects ? objects[i] : Array.get(array, i);
            sb.append(toSafeJson(element, scene, depth + 1));
        }
        sb.append(']');
        return sb.toString();
    }

    static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
