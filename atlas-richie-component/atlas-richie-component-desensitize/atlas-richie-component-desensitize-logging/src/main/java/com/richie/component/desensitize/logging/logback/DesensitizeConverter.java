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

