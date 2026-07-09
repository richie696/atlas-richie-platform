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
