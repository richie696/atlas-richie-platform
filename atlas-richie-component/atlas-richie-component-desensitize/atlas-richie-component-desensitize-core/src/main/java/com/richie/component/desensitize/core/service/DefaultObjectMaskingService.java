package com.richie.component.desensitize.core.service;

import com.richie.component.desensitize.core.serializer.SafeLogSerializer;

import java.util.Map;

/**
 * {@link ObjectMaskingService} 默认实现。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
public class DefaultObjectMaskingService implements ObjectMaskingService {

    /**
     * 依赖组件。
     */
    private final SafeLogSerializer safeLogSerializer;

    /**
     * 构造对象脱敏服务。
     *
     * @param safeLogSerializer 安全序列化器
     */
    public DefaultObjectMaskingService(SafeLogSerializer safeLogSerializer) {
        this.safeLogSerializer = safeLogSerializer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toSafeJson(Object value) {
        return safeLogSerializer.toSafeJson(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    /**
     * toSafeString。
     * @param Map<String 参数
     * @param map 参数
     * @return 处理结果
     */
    public String toSafeString(Map<String, ?> map) {
        return safeLogSerializer.toSafeString(map);
    }
}
