package com.richie.component.desensitize.core.serializer;

import com.richie.component.desensitize.core.DesensitizeTestSupport;
import com.richie.component.desensitize.core.annotation.Sensitive;
import com.richie.component.desensitize.core.config.DesensitizeProperties;
import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.model.MaskType;
import com.richie.component.desensitize.core.registry.MaskRuleRegistry;
import com.richie.component.desensitize.core.service.MaskingService;
import com.richie.component.desensitize.core.service.ObjectMaskingService;
import com.richie.component.desensitize.core.util.DesensitizeUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * SafeLogSerializerTest 测试类。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
class SafeLogSerializerTest {

    /**
     * 核心依赖组件。
     */
    private ObjectMaskingService objectMaskingService;

    @BeforeEach
    void setUp() {
        DesensitizeProperties properties = DesensitizeTestSupport.defaultProperties();
        MaskingService maskingService = DesensitizeTestSupport.maskingService(properties);
        objectMaskingService = DesensitizeTestSupport.defaultObjectMaskingService(maskingService, properties);
        DesensitizeUtils.bind(maskingService, objectMaskingService);
    }

    @AfterEach
    void tearDown() {
        DesensitizeUtils.clear();
    }

    @Test
    void toSafeJsonMasksAnnotatedField() {
        UserLogVo vo = new UserLogVo();
        vo.phone = "13812348000";
        vo.nickname = "小明";
        String json = objectMaskingService.toSafeJson(vo);
        assertTrue(json.contains("138****8000"));
        assertTrue(json.contains("小明"));
        assertFalse(json.contains("13812348000"));
    }

    @Test
    void toSafeStringMasksMapKeys() {
        String json = objectMaskingService.toSafeString(DesensitizeTestSupport.sampleRow());
        assertTrue(json.contains("138****8000"));
        assertTrue(json.contains("13812348000"));
    }

    @Test
    void rawToStringDoesNotApplySensitiveAnnotation() {
        UserLogVoWithToString vo = new UserLogVoWithToString();
        vo.phone = "13812348000";
        String raw = String.valueOf(vo);
        assertTrue(raw.contains("13812348000"), "裸 toString 会输出明文");
        assertFalse(DesensitizeUtils.toSafeJson(vo).contains("13812348000"));
        assertTrue(DesensitizeUtils.toSafeJson(vo).contains("138****8000"));
    }

    @Test
    void utilsToSafeJson() {
        UserLogVo vo = new UserLogVo();
        vo.phone = "13812348000";
        assertTrue(DesensitizeUtils.toSafeJson(vo).contains("138****8000"));
    }

    static class UserLogVo {
        @Sensitive(type = MaskType.PHONE, scenes = MaskScene.LOG)
        String phone;
        String nickname;
    }

    static class UserLogVoWithToString extends UserLogVo {
        /**
         * toString。
         * @return 处理结果
         */
        @Override
        public String toString() {
            return "UserLogVo(phone=" + phone + ", nickname=" + nickname + ")";
        }
    }
}
