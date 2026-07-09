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
package com.richie.component.desensitize.logging.service;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.richie.component.desensitize.core.support.SensitiveLogArg;
import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.util.DesensitizeUtils;
import org.slf4j.helpers.MessageFormatter;

import java.util.HashMap;
import java.util.Map;

/**
 * 默认日志脱敏服务：仅处理显式 {@link SensitiveLogArg} 参数。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
public class DefaultLoggingMaskingService implements LoggingMaskingService {

    /**
     * toMaskedMessage。
     * @param event 参数
     * @return 处理结果
     */
    @Override
    public String toMaskedMessage(ILoggingEvent event) {
        if (event == null) {
            return null;
        }
        Object[] args = event.getArgumentArray();
        if (args == null || args.length == 0) {
            return event.getFormattedMessage();
        }
        Object[] masked = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof SensitiveLogArg sensitive) {
                masked[i] = maskSensitiveArg(sensitive);
            } else {
                masked[i] = arg;
            }
        }
        return MessageFormatter.arrayFormat(event.getMessage(), masked).getMessage();
    }

    @Override
    /**
     * maskMdc。
     * @param Map<String 参数
     * @param mdcMap 参数
     * @return 处理结果
     */
    public Map<String, String> maskMdc(Map<String, String> mdcMap) {
        if (mdcMap == null || mdcMap.isEmpty()) {
            return mdcMap == null ? null : Map.of();
        }
        try {
            Map<String, Object> source = new HashMap<>();
            mdcMap.forEach(source::put);
            Map<String, Object> masked = DesensitizeUtils.maskMap(source, MaskScene.LOG);
            Map<String, String> result = new HashMap<>();
            masked.forEach((k, v) -> result.put(k, v == null ? null : String.valueOf(v)));
            return result;
        } catch (IllegalStateException ignored) {
            return new HashMap<>(mdcMap);
        }
    }

    private static String maskSensitiveArg(SensitiveLogArg arg) {
        if (arg == null) {
            return null;
        }
        try {
            return DesensitizeUtils.mask(arg.value(), arg.type());
        } catch (IllegalStateException ignored) {
            // 若 core 尚未初始化，回退原值避免日志链路中断
            return arg.value();
        }
    }
}

