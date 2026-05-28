package com.richie.component.desensitize.core.service;

import com.richie.component.desensitize.core.model.MaskContext;
import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.model.MaskType;

import java.util.LinkedHashMap;
import java.util.Map;
public interface MaskingService {

    /**
     * 使用默认场景执行脱敏。
     *
     * @param raw 原始字符串
     * @param type 脱敏类型
     * @return 脱敏后的字符串
     */
    String mask(String raw, MaskType type);

    /**
     * 在指定场景执行脱敏。
     *
     * @param raw 原始字符串
     * @param type 脱敏类型
     * @param scene 脱敏场景
     * @return 脱敏后的字符串
     */
    String mask(String raw, MaskType type, MaskScene scene);

    /**
     * 在完整上下文中执行脱敏。
     *
     * @param raw 原始字符串
     * @param context 脱敏上下文
     * @param type 脱敏类型
     * @return 脱敏后的字符串
     */
    String mask(String raw, MaskContext context, MaskType type);

    /**
     * 使用默认场景脱敏 Map 中的敏感键值。
     *
     * @param source 原始 Map
     * @return 脱敏后的 Map
     */
    Map<String, Object> maskMap(Map<String, ?> source);

    /**
     * 在指定场景脱敏 Map 中的敏感键值。
     *
     * @param source 原始 Map
     * @param scene 脱敏场景
     * @return 脱敏后的 Map
     */
    Map<String, Object> maskMap(Map<String, ?> source, MaskScene scene);
}
