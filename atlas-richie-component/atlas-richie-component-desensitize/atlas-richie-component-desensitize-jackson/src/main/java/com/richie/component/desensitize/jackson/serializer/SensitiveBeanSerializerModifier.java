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
package com.richie.component.desensitize.jackson.serializer;

import com.richie.component.desensitize.core.annotation.Sensitive;
import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.registry.SensitiveKeyRegistry;
import com.richie.component.desensitize.core.service.MaskingService;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.databind.ValueSerializer;

import java.util.List;
import java.util.Map;

/**
 * 为带 {@link Sensitive} 的 String 属性及 Map 属性绑定脱敏序列化器。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
public class SensitiveBeanSerializerModifier extends ValueSerializerModifier {

    /**
     * 核心依赖组件。
     */
    private final MaskingService maskingService;
    /**
     * 核心依赖组件。
     */
    private final SensitiveKeyRegistry sensitiveKeyRegistry;

    /**
     * 构造序列化器修饰器。
     *
     * @param maskingService 脱敏服务
     * @param sensitiveKeyRegistry 敏感键注册表
     */
    public SensitiveBeanSerializerModifier(MaskingService maskingService, SensitiveKeyRegistry sensitiveKeyRegistry) {
        this.maskingService = maskingService;
        this.sensitiveKeyRegistry = sensitiveKeyRegistry;
    }

    /**
     * 为 Bean 属性动态替换脱敏序列化器。
     *
     * @param config 序列化配置
     * @param beanDescRef Bean 描述提供者
     * @param beanProperties 属性写入器列表
     * @return 处理后的属性写入器列表
     */
    @Override
    public List<BeanPropertyWriter> changeProperties(
            SerializationConfig config,
            BeanDescription.Supplier beanDescRef,
            List<BeanPropertyWriter> beanProperties) {
        BeanDescription beanDesc = beanDescRef.get();
        Class<?> beanClass = beanDesc.getBeanClass();
        for (BeanPropertyWriter writer : beanProperties) {
            Sensitive sensitive = writer.getAnnotation(Sensitive.class);
            if (sensitive != null && CharSequence.class.isAssignableFrom(writer.getType().getRawClass())) {
                // 注解字符串字段：按字段元数据绑定脱敏序列化器。
                MaskScene scene = resolveApiScene(sensitive);
                writer.assignSerializer(asObjectSerializer(new SensitiveValueSerializer(
                        maskingService,
                        sensitive.type(),
                        scene,
                        writer.getName(),
                        beanClass)));
            } else if (Map.class.isAssignableFrom(writer.getType().getRawClass())) {
                // Map 字段：按敏感键规则递归脱敏。
                writer.assignSerializer(asObjectSerializer(new SensitiveMapSerializer(
                        maskingService, sensitiveKeyRegistry, MaskScene.API_RESPONSE)));
            }
        }
        return beanProperties;
    }

    @SuppressWarnings("unchecked")
    private static ValueSerializer<Object> asObjectSerializer(ValueSerializer<?> serializer) {
        return (ValueSerializer<Object>) serializer;
    }

    private static MaskScene resolveApiScene(Sensitive sensitive) {
        for (MaskScene scene : sensitive.scenes()) {
            if (scene == MaskScene.API_RESPONSE) {
                return MaskScene.API_RESPONSE;
            }
        }
        return MaskScene.API_RESPONSE;
    }
}
