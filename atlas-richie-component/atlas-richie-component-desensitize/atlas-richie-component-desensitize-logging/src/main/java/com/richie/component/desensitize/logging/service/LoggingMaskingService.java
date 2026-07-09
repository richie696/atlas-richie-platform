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
package com.richie.component.desensitize.logging.service;

import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.Map;
public interface LoggingMaskingService {

    /**
     * 将日志事件渲染为脱敏后的消息。
     *
     * @param event Logback 事件
     * @return 脱敏后的消息文本
     */
    String toMaskedMessage(ILoggingEvent event);

    /**
     * 按 `sensitive-keys` 对 MDC 字段进行脱敏（用于结构化日志输出）。
     *
     * @param mdcMap MDC 键值
     * @return 脱敏后的 MDC（新 Map）
     */
    Map<String, String> maskMdc(Map<String, String> mdcMap);
}

