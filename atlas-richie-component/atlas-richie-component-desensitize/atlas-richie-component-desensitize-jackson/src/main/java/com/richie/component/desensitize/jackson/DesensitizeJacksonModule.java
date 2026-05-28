package com.richie.component.desensitize.jackson;

import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.registry.SensitiveKeyRegistry;
import com.richie.component.desensitize.core.service.MaskingService;
import com.richie.component.desensitize.jackson.serializer.SensitiveBeanSerializerModifier;
import com.richie.component.desensitize.jackson.serializer.SensitiveMapSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Jackson 脱敏模块。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
public class DesensitizeJacksonModule extends SimpleModule {

    /**
     * 构造 Jackson 脱敏模块并注册属性/Map 序列化器。
     *
     * @param maskingService 脱敏服务
     * @param sensitiveKeyRegistry 敏感键注册表
     */
    public DesensitizeJacksonModule(MaskingService maskingService, SensitiveKeyRegistry sensitiveKeyRegistry) {
        super("desensitize-jackson");
        setSerializerModifier(new SensitiveBeanSerializerModifier(maskingService, sensitiveKeyRegistry));
        addSerializer(new SensitiveMapSerializer(maskingService, sensitiveKeyRegistry, MaskScene.API_RESPONSE));
    }
}
