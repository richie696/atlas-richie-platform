/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
     *
     * @param map 原始 Map
     * @return 返回脱敏后的字符串
     */
    @Override
    public String toSafeString(Map<String, ?> map) {
        return safeLogSerializer.toSafeString(map);
    }
}
