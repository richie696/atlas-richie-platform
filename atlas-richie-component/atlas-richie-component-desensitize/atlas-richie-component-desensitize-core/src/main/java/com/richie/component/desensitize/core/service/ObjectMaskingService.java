package com.richie.component.desensitize.core.service;

import java.util.Map;

/**
 * 对象 / Map 安全序列化（日志等场景）。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
public interface ObjectMaskingService {

    /**
     * 将任意对象序列化为安全 JSON。
     *
     * @param value 原始对象
     * @return 安全 JSON 字符串
     */
    String toSafeJson(Object value);

    /**
     * 将 Map 序列化为安全字符串。
     *
     * @param map 原始 Map
     * @return 安全字符串
     */
    String toSafeString(Map<String, ?> map);
}
