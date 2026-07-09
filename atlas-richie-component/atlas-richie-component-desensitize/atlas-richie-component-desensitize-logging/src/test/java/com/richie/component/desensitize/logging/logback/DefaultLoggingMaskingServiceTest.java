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
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
/**
 * DefaultLoggingMaskingServiceTest 测试类。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
class DefaultLoggingMaskingServiceTest {

    private final LoggingMaskingService loggingMaskingService = new DefaultLoggingMaskingService();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    com.richie.component.desensitize.core.config.DesensitizeAutoConfiguration.class))
            .withPropertyValues("platform.component.desensitize.sensitive-keys.phone=PHONE");

    @BeforeEach
    void setUp() {
        contextRunner.run(context -> {
            // core 自动配置会绑定 DesensitizeUtils，这里触发一次初始化
            assertThat(context).hasSingleBean(com.richie.component.desensitize.core.service.MaskingService.class);
        });
    }

    @AfterEach
    void tearDown() {
        DesensitizeUtils.clear();
    }

    @Test
    void shouldMaskSensitiveLogArg() {
        LoggingEvent event = new LoggingEvent();
        event.setLoggerName("test");
        event.setLevel(Level.INFO);
        event.setMessage("phone={}, orderId={}");
        event.setArgumentArray(new Object[]{
                SensitiveLogArg.of("13812348000", MaskType.PHONE),
                "13812348000"
        });
        event.setLoggerContextRemoteView(new LoggerContext().getLoggerContextRemoteView());

        String masked = loggingMaskingService.toMaskedMessage(event);
        assertThat(masked).contains("138****8000");
        assertThat(masked).contains("orderId=13812348000");
    }

    @Test
    void shouldFallbackToFormattedMessageWhenNoArgs() {
        LoggingEvent event = new LoggingEvent();
        event.setMessage("plain message");
        event.setArgumentArray(null);
        event.setLoggerContextRemoteView(new LoggerContext().getLoggerContextRemoteView());

        String masked = loggingMaskingService.toMaskedMessage(event);
        assertThat(masked).isEqualTo("plain message");
    }

    @Test
    void turboFilterShouldMaskSensitiveArgsInPlace() {
        SensitiveLogArgTurboFilter turboFilter = new SensitiveLogArgTurboFilter();
        Object[] params = new Object[]{
                SensitiveLogArg.phone("13812348000"),
                "OID-13812348000"
        };

        turboFilter.decide(null, null, Level.INFO, "phone={}, order={}", params, null);

        assertThat(params[0]).isEqualTo("138****8000");
        assertThat(params[1]).isEqualTo("OID-13812348000");
    }

    @Test
    void mdcTurboFilterShouldMaskSensitiveKeys() {
        SensitiveMdcTurboFilter turboFilter = new SensitiveMdcTurboFilter(loggingMaskingService);
        MDC.put("phone", "13812348000");
        MDC.put("traceId", "T-1");
        try {
            turboFilter.decide(null, null, Level.INFO, "mdc test", null, null);
            assertThat(MDC.get("phone")).isEqualTo("138****8000");
            assertThat(MDC.get("traceId")).isEqualTo("T-1");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void jsonConverterShouldMaskJsonMessageBySensitiveKeys() {
        DesensitizeJsonMessageConverter converter = new DesensitizeJsonMessageConverter();
        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.INFO);
        event.setMessage("{\"phone\":\"13812348000\",\"orderId\":\"OID-1\"}");
        event.setArgumentArray(null);
        event.setLoggerContextRemoteView(new LoggerContext().getLoggerContextRemoteView());

        String output = converter.convert(event);
        assertThat(output).contains("\"phone\":\"138****8000\"");
        assertThat(output).contains("\"orderId\":\"OID-1\"");
    }

    @Test
    void jsonConverterShouldKeepPlainTextWhenNotJson() {
        DesensitizeJsonMessageConverter converter = new DesensitizeJsonMessageConverter();
        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.INFO);
        event.setMessage("plain text log");
        event.setArgumentArray(null);
        event.setLoggerContextRemoteView(new LoggerContext().getLoggerContextRemoteView());

        String output = converter.convert(event);
        assertThat(output).isEqualTo("plain text log");
    }
}

