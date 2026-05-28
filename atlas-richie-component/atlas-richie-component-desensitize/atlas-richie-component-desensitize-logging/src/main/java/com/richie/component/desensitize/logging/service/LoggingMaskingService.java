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

