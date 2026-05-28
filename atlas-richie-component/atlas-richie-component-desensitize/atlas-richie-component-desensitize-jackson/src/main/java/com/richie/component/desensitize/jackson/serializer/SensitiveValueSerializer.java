package com.richie.component.desensitize.jackson.serializer;

import com.richie.component.desensitize.core.annotation.Sensitive;
import com.richie.component.desensitize.core.model.MaskContext;
import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.model.MaskType;
import com.richie.component.desensitize.core.service.MaskingService;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * 对带 {@link Sensitive} 的 String 字段序列化时脱敏。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
public class SensitiveValueSerializer extends StdSerializer<String> {

    /**
     * 核心依赖组件。
     */
    private final MaskingService maskingService;
    /**
     * 依赖组件。
     */
    private final MaskType maskType;
    /**
     * 依赖组件。
     */
    private final MaskScene scene;
    /**
     * 依赖组件。
     */
    private final String fieldName;
    /**
     * 依赖组件。
     */
    private final Class<?> declaringClass;

    /**
     * 构造字段级脱敏序列化器。
     *
     * @param maskingService 脱敏服务
     * @param maskType 脱敏类型
     * @param scene 脱敏场景
     * @param fieldName 字段名
     * @param declaringClass 声明类
     */
    public SensitiveValueSerializer(
            MaskingService maskingService,
            MaskType maskType,
            MaskScene scene,
            String fieldName,
            Class<?> declaringClass) {
        super(String.class);
        this.maskingService = maskingService;
        this.maskType = maskType;
        this.scene = scene;
        this.fieldName = fieldName;
        this.declaringClass = declaringClass;
    }

    /**
     * 序列化字符串字段，并按上下文执行脱敏。
     *
     * @param value 原始值
     * @param gen Json 生成器
     * @param ctxt 序列化上下文
     */
    @Override
    public void serialize(String value, JsonGenerator gen, SerializationContext ctxt) {
        if (value == null) {
            gen.writeNull();
            return;
        }
        String masked = maskingService.mask(value, MaskContext.of(scene, fieldName, declaringClass), maskType);
        gen.writeString(masked);
    }

    /**
     * 基于属性注解创建上下文化序列化器。
     *
     * @param ctxt 序列化上下文
     * @param property 当前属性
     * @return 序列化器实例
     */
    @Override
    public ValueSerializer<?> createContextual(
            SerializationContext ctxt,
            BeanProperty property) {
        if (property == null) {
            return this;
        }
        Sensitive sensitive = property.getAnnotation(Sensitive.class);
        if (sensitive == null) {
            return this;
        }
        // 上下文化阶段按属性注解重新计算场景与类型。
        MaskScene scene = resolveScene(sensitive);
        return new SensitiveValueSerializer(
                maskingService,
                sensitive.type(),
                scene,
                property.getName(),
                property.getMember().getDeclaringClass());
    }

    private static MaskScene resolveScene(Sensitive sensitive) {
        for (MaskScene scene : sensitive.scenes()) {
            if (scene == MaskScene.API_RESPONSE) {
                return MaskScene.API_RESPONSE;
            }
        }
        return sensitive.scenes().length > 0 ? sensitive.scenes()[0] : MaskScene.API_RESPONSE;
    }
}
