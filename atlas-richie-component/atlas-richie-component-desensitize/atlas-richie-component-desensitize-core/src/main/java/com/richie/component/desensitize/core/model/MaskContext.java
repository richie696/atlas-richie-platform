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
