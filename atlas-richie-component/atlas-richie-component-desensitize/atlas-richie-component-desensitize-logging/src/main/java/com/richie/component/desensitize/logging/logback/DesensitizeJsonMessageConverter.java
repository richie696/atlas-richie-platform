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
package com.richie.component.desensitize.logging.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.richie.context.utils.data.JsonUtils;
import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.util.DesensitizeUtils;
import tools.jackson.core.type.TypeReference;

import java.util.Map;

/**
 * JSON 消息脱敏转换器：用于 `%desensitizeJsonMsg`。
 *
 * <p>当日志消息是 JSON 字符串时，按 `sensitive-keys` 对字段值脱敏；非 JSON 文本原样返回。</p>
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
public class DesensitizeJsonMessageConverter extends DesensitizeConverter {

    /**
     * convert。
     * @param event 参数
     * @return 处理结果
     */
    @Override
    public String convert(ILoggingEvent event) {
        String message = super.convert(event);
        if (message == null || message.isBlank()) {
            return message;
        }
        String trimmed = message.trim();
        if (!(trimmed.startsWith("{") && trimmed.endsWith("}"))) {
            return message;
        }
        try {
            Map<String, Object> jsonMap = JsonUtils.getInstance().deserialize(trimmed, new TypeReference<>() {
            });
            if (jsonMap == null) {
                return message;
            }
            Map<String, Object> masked = DesensitizeUtils.maskMap(jsonMap, MaskScene.LOG);
            String serialized = JsonUtils.getInstance().serialize(masked);
            return serialized == null ? message : serialized;
        } catch (Exception ignored) {
            return message;
        }
    }
}

