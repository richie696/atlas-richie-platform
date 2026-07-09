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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import com.richie.component.desensitize.logging.service.LoggingMaskingService;
import org.slf4j.MDC;
import org.slf4j.Marker;

import java.util.Map;

/**
 * 在日志事件创建前对 MDC 执行脱敏，适配 JSON Layout includeMdc 场景。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
public class SensitiveMdcTurboFilter extends TurboFilter {

    /**
     * 核心依赖组件。
     */
    private final LoggingMaskingService loggingMaskingService;

    public SensitiveMdcTurboFilter(LoggingMaskingService loggingMaskingService) {
        this.loggingMaskingService = loggingMaskingService;
    }

    @Override
    public FilterReply decide(
            Marker marker,
            Logger logger,
            Level level,
            String format,
            Object[] params,
            Throwable t) {
        Map<String, String> current = MDC.getCopyOfContextMap();
        if (current == null || current.isEmpty()) {
            return FilterReply.NEUTRAL;
        }
        Map<String, String> masked = loggingMaskingService.maskMdc(current);
        if (masked == null || masked.equals(current)) {
            return FilterReply.NEUTRAL;
        }
        for (Map.Entry<String, String> entry : masked.entrySet()) {
            if (entry.getValue() == null) {
                MDC.remove(entry.getKey());
            } else {
                MDC.put(entry.getKey(), entry.getValue());
            }
        }
        return FilterReply.NEUTRAL;
    }
}

