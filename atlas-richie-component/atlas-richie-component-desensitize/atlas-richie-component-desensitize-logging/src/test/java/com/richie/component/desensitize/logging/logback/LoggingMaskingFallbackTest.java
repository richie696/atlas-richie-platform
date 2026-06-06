package com.richie.component.desensitize.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.richie.component.desensitize.core.model.MaskType;
import com.richie.component.desensitize.core.support.SensitiveLogArg;
import com.richie.component.desensitize.core.util.DesensitizeUtils;
import com.richie.component.desensitize.logging.service.DefaultLoggingMaskingService;
import com.richie.component.desensitize.logging.service.LoggingMaskingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingMaskingFallbackTest {

    private final DefaultLoggingMaskingService service = new DefaultLoggingMaskingService();

    @BeforeEach
    @AfterEach
    void clearUtilsBinding() {
        DesensitizeUtils.clear();
    }

    @Test
    void toMaskedMessageReturnsNullForNullEvent() {
        assertThat(service.toMaskedMessage(null)).isNull();
    }

    @Test
    void maskMdcReturnsEmptyMapForEmptyInput() {
        assertThat(service.maskMdc(Map.of())).isEmpty();
        assertThat(service.maskMdc(null)).isNull();
    }

    @Test
    void maskMdcFallsBackWhenUtilsNotInitialized() {
        Map<String, String> source = Map.of("phone", "13812348000", "traceId", "T-1");

        Map<String, String> masked = service.maskMdc(source);

        assertThat(masked).containsEntry("phone", "13812348000");
        assertThat(masked).containsEntry("traceId", "T-1");
    }

    @Test
    void sensitiveArgFallsBackWhenUtilsNotInitialized() {
        LoggingEvent event = new LoggingEvent();
        event.setMessage("phone={}");
        event.setArgumentArray(new Object[]{SensitiveLogArg.phone("13812348000")});
        event.setLoggerContextRemoteView(new LoggerContext().getLoggerContextRemoteView());

        String message = service.toMaskedMessage(event);

        assertThat(message).contains("13812348000");
    }

    @Test
    void turboFilterNeutralWhenNoParams() {
        SensitiveLogArgTurboFilter filter = new SensitiveLogArgTurboFilter();
        assertThat(filter.decide(null, null, Level.INFO, "msg", null, null)).isNotNull();
    }

    @Test
    void desensitizeConverterDelegatesToService() {
        DesensitizeConverter converter = new DesensitizeConverter();
        LoggingEvent event = new LoggingEvent();
        event.setMessage("plain");
        event.setLoggerContextRemoteView(new LoggerContext().getLoggerContextRemoteView());

        assertThat(converter.convert(event)).isEqualTo("plain");
    }

    @Test
    void mdcTurboFilterIgnoresEmptyMdc() {
        SensitiveMdcTurboFilter filter = new SensitiveMdcTurboFilter(service);
        assertThat(filter.decide(null, null, Level.INFO, "msg", null, null)).isNotNull();
    }

    @Test
    void mdcTurboFilterRemovesNullMaskedValues() {
        LoggingMaskingService maskingService = new LoggingMaskingService() {
            @Override
            public String toMaskedMessage(ch.qos.logback.classic.spi.ILoggingEvent event) {
                return null;
            }

            @Override
            public Map<String, String> maskMdc(Map<String, String> mdcMap) {
                Map<String, String> masked = new java.util.HashMap<>();
                masked.put("phone", null);
                masked.put("traceId", "T-1");
                return masked;
            }
        };
        SensitiveMdcTurboFilter filter = new SensitiveMdcTurboFilter(maskingService);
        MDC.put("phone", "13812348000");
        MDC.put("traceId", "T-1");
        try {
            filter.decide(null, null, Level.INFO, "msg", null, null);
            assertThat(MDC.get("phone")).isNull();
            assertThat(MDC.get("traceId")).isEqualTo("T-1");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void jsonConverterReturnsBlankMessageAsIs() {
        DesensitizeJsonMessageConverter converter = new DesensitizeJsonMessageConverter();
        LoggingEvent event = new LoggingEvent();
        event.setMessage("   ");
        event.setLoggerContextRemoteView(new LoggerContext().getLoggerContextRemoteView());

        assertThat(converter.convert(event)).isEqualTo("   ");
    }

    @Test
    void jsonConverterReturnsInvalidJsonAsIs() {
        DesensitizeJsonMessageConverter converter = new DesensitizeJsonMessageConverter();
        LoggingEvent event = new LoggingEvent();
        event.setMessage("{not-json");
        event.setLoggerContextRemoteView(new LoggerContext().getLoggerContextRemoteView());

        assertThat(converter.convert(event)).isEqualTo("{not-json");
    }
}
