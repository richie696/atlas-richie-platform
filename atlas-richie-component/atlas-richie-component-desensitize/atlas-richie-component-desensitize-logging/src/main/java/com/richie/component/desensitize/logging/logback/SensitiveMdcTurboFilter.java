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

