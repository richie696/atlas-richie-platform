/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.parser;

import java.time.Duration;
import java.util.Map;

/**
 * 解析上下文,携带超时、最大段落长度、调用方自定义属性等解析参数。
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
public record ParserContext(
        Duration timeout,
        Integer maxSegmentLength,
        Map<String, Object> attributes
) {
    public ParserContext {
        if (timeout == null) {
            timeout = Duration.ofSeconds(60);
        }
        if (attributes == null) {
            attributes = Map.of();
        }
    }

    /**
     * 默认解析上下文(60 秒超时,无段落长度上限)。
     */
    public static ParserContext defaults() {
        return new ParserContext(Duration.ofSeconds(60), null, Map.of());
    }
}
