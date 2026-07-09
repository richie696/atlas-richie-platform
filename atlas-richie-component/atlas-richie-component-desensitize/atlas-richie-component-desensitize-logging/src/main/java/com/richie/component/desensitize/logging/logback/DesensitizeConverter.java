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

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.richie.component.desensitize.logging.service.DefaultLoggingMaskingService;
import com.richie.component.desensitize.logging.service.LoggingMaskingService;

/**
 * Logback 转换器：用于 `%desensitizeMsg` 输出脱敏后的日志消息。
 *
 * <p>logback-spring.xml 示例：
 * <pre>{@code
 * <conversionRule conversionWord="desensitizeMsg"
 *     converterClass="com.richie.component.desensitize.logging.logback.DesensitizeConverter"/>
 * <pattern>%d %-5level %logger - %desensitizeMsg%n</pattern>
 * }</pre>
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
public class DesensitizeConverter extends ClassicConverter {

    private final LoggingMaskingService loggingMaskingService = new DefaultLoggingMaskingService();

    /**
     * convert。
     * @param event 参数
     * @return 处理结果
     */
    @Override
    public String convert(ILoggingEvent event) {
        return loggingMaskingService.toMaskedMessage(event);
    }
}

