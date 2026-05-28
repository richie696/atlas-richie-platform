package com.richie.component.desensitize.sample.model;

import com.richie.component.desensitize.core.annotation.Sensitive;
import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.model.MaskType;
import lombok.Data;

import java.util.Map;

/**
 * 示例返回对象：演示注解字段 + Map 字段脱敏。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
@Data
public class UserProfileView {

    private String userId;

    @Sensitive(type = MaskType.PHONE, scenes = {MaskScene.API_RESPONSE, MaskScene.LOG})
    private String phone;

    @Sensitive(type = MaskType.ID_CARD, scenes = {MaskScene.API_RESPONSE, MaskScene.LOG})
    private String idCard;

    private Map<String, Object> extra;
}

