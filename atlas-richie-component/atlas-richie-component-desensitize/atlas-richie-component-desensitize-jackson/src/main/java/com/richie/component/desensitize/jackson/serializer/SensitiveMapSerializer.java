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

import com.richie.component.desensitize.core.model.MaskContext;
import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.model.MaskType;
import com.richie.component.desensitize.core.registry.SensitiveKeyRegistry;
import com.richie.component.desensitize.core.service.MaskingService;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

import java.util.Map;
import java.util.Optional;

/**
 * Map 序列化：按 {@code sensitive-keys} 脱敏 entry value。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
public class SensitiveMapSerializer extends StdSerializer<Map<?, ?>> {

    /**
     * 核心依赖组件。
     */
    private final MaskingService maskingService;
    /**
     * 核心依赖组件。
     */
    private final SensitiveKeyRegistry sensitiveKeyRegistry;
    /**
     * 依赖组件。
     */
    private final MaskScene scene;

    /**
     * 构造 Map 脱敏序列化器。
     *
     * @param maskingService 脱敏服务
     * @param sensitiveKeyRegistry 敏感键注册表
     * @param scene 脱敏场景
     */
    public SensitiveMapSerializer(
            MaskingService maskingService,
            SensitiveKeyRegistry sensitiveKeyRegistry,
            MaskScene scene) {
        super(Map.class);
        this.maskingService = maskingService;
        this.sensitiveKeyRegistry = sensitiveKeyRegistry;
        this.scene = scene;
    }

    /**
     * 序列化 Map，并按敏感键规则处理字符串值。
     *
     * @param value Map 值
     * @param gen Json 生成器
     * @param ctxt 序列化上下文
     */
    @Override
    /**
     * serialize。
     * @param Map<? 参数
     * @param value 参数
     * @param gen 参数
     * @param ctxt 参数
     */
    public void serialize(Map<?, ?> value, JsonGenerator gen, SerializationContext ctxt) {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeStartObject();
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            String key = String.valueOf(entry.getKey());
            gen.writeName(key);
            writeValue(entry.getValue(), key, gen, ctxt);
        }
        gen.writeEndObject();
    }

    @SuppressWarnings("unchecked")
    private void writeValue(Object raw, String key, JsonGenerator gen, SerializationContext ctxt) {
        if (raw == null) {
            gen.writeNull();
            return;
        }
        if (raw instanceof String str) {
            Optional<MaskType> type = sensitiveKeyRegistry.resolve(key, scene);
            if (type.isPresent()) {
                gen.writeString(maskingService.mask(str, MaskContext.of(scene, key, null), type.get()));
            } else {
                gen.writeString(str);
            }
            return;
        }
        if (raw instanceof Map<?, ?> nested) {
            // 对嵌套 Map 递归执行相同规则。
            serialize(nested, gen, ctxt);
            return;
        }
        gen.writePOJO(raw);
    }
}
