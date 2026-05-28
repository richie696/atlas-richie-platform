package com.richie.component.desensitize.core.registry;

import com.richie.component.desensitize.core.annotation.Sensitive;
import com.richie.component.desensitize.core.config.DesensitizeProperties;
import com.richie.component.desensitize.core.model.MaskRule;
import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.model.MaskType;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * 合并 YAML 字段规则与 {@link Sensitive} 注解元数据。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
public class MaskRuleRegistry {

    /**
     * 依赖组件。
     */
    private final DesensitizeProperties properties;

    /**
     * 构造字段规则注册表。
     *
     * @param properties 脱敏配置
     */
    public MaskRuleRegistry(DesensitizeProperties properties) {
        this.properties = properties;
    }

    /**
     * 解析字段在指定场景下的脱敏类型。
     *
     * @param declaringClass 字段所属类型
     * @param fieldName 字段名
     * @param scene 脱敏场景
     * @return 脱敏类型（若存在）
     */
    public Optional<MaskType> resolveFieldType(Class<?> declaringClass, String fieldName, MaskScene scene) {
        Optional<MaskType> fromAnnotation = resolveFromAnnotation(declaringClass, fieldName, scene);
        if (fromAnnotation.isPresent()) {
            return fromAnnotation;
        }
        if (declaringClass == null || fieldName == null) {
            return Optional.empty();
        }
        Map<String, MaskType> classRules = properties.getFields().get(declaringClass.getName());
        if (classRules == null) {
            return Optional.empty();
        }
        MaskType type = classRules.get(fieldName);
        return Optional.ofNullable(type);
    }

    /**
     * 将脱敏类型转换为运行时规则。
     *
     * @param type 脱敏类型
     * @return 脱敏规则
     */
    public MaskRule toRule(MaskType type) {
        DesensitizeProperties.TypeRule typeRule = properties.getTypeRules().get(type);
        int keepLeft = typeRule != null && typeRule.getKeepLeft() != null
                ? typeRule.getKeepLeft()
                : MaskRule.defaultKeepLeft(type);
        int keepRight = typeRule != null && typeRule.getKeepRight() != null
                ? typeRule.getKeepRight()
                : MaskRule.defaultKeepRight(type);
        char maskChar = typeRule != null && typeRule.getMaskChar() != null
                ? typeRule.getMaskChar()
                : properties.getDefaultMaskChar();
        return new MaskRule(type, keepLeft, keepRight, maskChar, null);
    }

    private Optional<MaskType> resolveFromAnnotation(Class<?> declaringClass, String fieldName, MaskScene scene) {
        if (declaringClass == null || fieldName == null) {
            return Optional.empty();
        }
        try {
            Field field = findField(declaringClass, fieldName);
            Sensitive sensitive = field.getAnnotation(Sensitive.class);
            if (sensitive == null) {
                return Optional.empty();
            }
            boolean sceneMatch = Arrays.stream(sensitive.scenes()).anyMatch(s -> s == scene);
            if (!sceneMatch) {
                return Optional.empty();
            }
            return Optional.of(sensitive.type());
        } catch (NoSuchFieldException ex) {
            return Optional.empty();
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
