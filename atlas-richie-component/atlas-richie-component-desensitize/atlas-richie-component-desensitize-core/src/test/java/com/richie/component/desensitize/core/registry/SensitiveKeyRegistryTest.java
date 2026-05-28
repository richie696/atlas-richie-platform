package com.richie.component.desensitize.core.registry;

import com.richie.component.desensitize.core.config.DesensitizeProperties;
import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.model.MaskType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * SensitiveKeyRegistryTest 测试类。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
class SensitiveKeyRegistryTest {

    @Test
    void resolveIgnoresCase() {
        DesensitizeProperties properties = new DesensitizeProperties();
        properties.getSensitiveKeys().put("phone", MaskType.PHONE);
        SensitiveKeyRegistry registry = new SensitiveKeyRegistry(properties);
        assertEquals(MaskType.PHONE, registry.resolve("Phone", MaskScene.API_RESPONSE).orElseThrow());
    }

    @Test
    void sceneOverride() {
        DesensitizeProperties properties = new DesensitizeProperties();
        properties.getSensitiveKeys().put("token", MaskType.PASSWORD);
        properties.getApiResponse().getSensitiveKeys().put("token", MaskType.EMAIL);
        SensitiveKeyRegistry registry = new SensitiveKeyRegistry(properties);
        assertEquals(MaskType.EMAIL, registry.resolve("token", MaskScene.API_RESPONSE).orElseThrow());
        assertEquals(MaskType.PASSWORD, registry.resolve("token", MaskScene.LOG).orElseThrow());
    }

    @Test
    void unknownKeyEmpty() {
        SensitiveKeyRegistry registry = new SensitiveKeyRegistry(new DesensitizeProperties());
        assertTrue(registry.resolve("unknown", MaskScene.LOG).isEmpty());
    }
}
