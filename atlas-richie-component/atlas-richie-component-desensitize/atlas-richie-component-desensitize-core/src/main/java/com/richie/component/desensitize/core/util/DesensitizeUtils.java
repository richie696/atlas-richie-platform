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
package com.richie.component.desensitize.core.util;

import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.model.MaskType;
import com.richie.component.desensitize.core.service.MaskingService;
import com.richie.component.desensitize.core.service.ObjectMaskingService;

import java.util.Map;
import java.util.Objects;

/**
 * 脱敏静态工具门面，委托 Spring 容器中的服务实现。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
public final class DesensitizeUtils {

    /**
     * 核心依赖组件。
     */
    private static volatile MaskingService maskingService;
    /**
     * 核心依赖组件。
     */
    private static volatile ObjectMaskingService objectMaskingService;

    private DesensitizeUtils() {
    }

    /**
     * 绑定底层脱敏服务实现。
     *
     * @param masking 字符串/Map 脱敏服务
     * @param objectMasking 对象脱敏服务
     */
    public static void bind(MaskingService masking, ObjectMaskingService objectMasking) {
        maskingService = Objects.requireNonNull(masking, "maskingService");
        objectMaskingService = Objects.requireNonNull(objectMasking, "objectMaskingService");
    }

    /**
     * 清理静态绑定（主要用于测试隔离）。
     */
    public static void clear() {
        maskingService = null;
        objectMaskingService = null;
    }

    /**
     * 使用默认场景执行脱敏。
     *
     * @param raw 原始字符串
     * @param type 脱敏类型
     * @return 脱敏结果
     */
    public static String mask(String raw, MaskType type) {
        return requireMasking().mask(raw, type);
    }

    /**
     * 使用指定场景执行脱敏。
     *
     * @param raw 原始字符串
     * @param type 脱敏类型
     * @param scene 脱敏场景
     * @return 脱敏结果
     */
    public static String mask(String raw, MaskType type, MaskScene scene) {
        return requireMasking().mask(raw, type, scene);
    }

    /**
     * 使用默认场景脱敏 Map。
     *
     * @param source 原始 Map
     * @return 脱敏后 Map
     */
    public static Map<String, Object> maskMap(Map<String, ?> source) {
        return requireMasking().maskMap(source);
    }

    /**
     * 使用指定场景脱敏 Map。
     *
     * @param source 原始 Map
     * @param scene 脱敏场景
     * @return 脱敏后 Map
     */
    public static Map<String, Object> maskMap(Map<String, ?> source, MaskScene scene) {
        return requireMasking().maskMap(source, scene);
    }

    /**
     * 将对象序列化为日志安全 JSON。
     *
     * @param value 原始对象
     * @return 安全 JSON
     */
    public static String toSafeJson(Object value) {
        return requireObjectMasking().toSafeJson(value);
    }

    /**
     * 将 Map 序列化为日志安全字符串。
     *
     * @param map 原始 Map
     * @return 安全字符串
     */
    public static String toSafeString(Map<String, ?> map) {
        return requireObjectMasking().toSafeString(map);
    }

    private static MaskingService requireMasking() {
        MaskingService service = maskingService;
        if (service == null) {
            throw new IllegalStateException(
                    "DesensitizeUtils is not initialized. Ensure desensitize-core auto-configuration is active.");
        }
        return service;
    }

    private static ObjectMaskingService requireObjectMasking() {
        ObjectMaskingService service = objectMaskingService;
        if (service == null) {
            throw new IllegalStateException(
                    "DesensitizeUtils is not initialized. Ensure desensitize-core auto-configuration is active.");
        }
        return service;
    }
}
