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
package com.richie.component.desensitize.core.model;

import java.util.Collections;
import java.util.Set;
public record MaskContext(
        MaskScene scene,
        String fieldName,
        Class<?> declaringClass,
        Set<String> roles
) {

    /**
     * 创建仅包含场景的上下文。
     *
     * @param scene 脱敏场景
     * @return 上下文实例
     */
    public static MaskContext of(MaskScene scene) {
        return new MaskContext(scene, null, null, Collections.emptySet());
    }

    /**
     * 创建包含字段信息的上下文。
     *
     * @param scene 脱敏场景
     * @param fieldName 字段名
     * @param declaringClass 声明类
     * @return 上下文实例
     */
    public static MaskContext of(MaskScene scene, String fieldName, Class<?> declaringClass) {
        return new MaskContext(scene, fieldName, declaringClass, Collections.emptySet());
    }

    /**
     * 复制当前上下文并附加角色集合。
     *
     * @param roles 当前用户角色
     * @return 新上下文实例
     */
    public MaskContext withRoles(Set<String> roles) {
        return new MaskContext(scene, fieldName, declaringClass,
                roles == null ? Collections.emptySet() : Set.copyOf(roles));
    }
}
