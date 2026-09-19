package cn.richie696.component.observability.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.richie696.component.observability.core.ObservabilityState;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityMdcFilterTest {

    @AfterEach
    void clearMdc() {
        org.slf4j.MDC.clear();
    }

    @Test
    void preservesRequestIdAndCleansMdcAfterRequest() throws Exception {
        ObservabilityMdcFilter filter = new ObservabilityMdcFilter(ObservabilityState.enabledState());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ObservabilityMdcFilter.REQUEST_ID_HEADER, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            invoked.set(true);
            assertThat(org.slf4j.MDC.get("request_id")).isEqualTo("request-123");
            assertThat(((MockHttpServletResponse) servletResponse)
                    .getHeader(ObservabilityMdcFilter.REQUEST_ID_HEADER)).isEqualTo("request-123");
        });

        assertThat(invoked).isTrue();
        assertThat(org.slf4j.MDC.get("request_id")).isNull();
    }

    @Test
    void rejectsUnsafeRequestId() {
        assertThat(ObservabilityMdcFilter.safeRequestId("unsafe value"))
                .isNotEqualTo("unsafe value")
                .isNotBlank();
        assertThat(ObservabilityMdcFilter.safeRequestId("safe_request-1"))
                .isEqualTo("safe_request-1");
        assertThat(ObservabilityMdc.safeValue("Authorization", "Bearer secret"))
                .isEqualTo("[REDACTED]");
        assertThat(ObservabilityMdc.safeValue("operation", "x".repeat(600)))
                .hasSize(512);
    }

    @Test
    void disabledStateDoesNotAddCorrelationHeader() throws Exception {
        ObservabilityMdcFilter filter = new ObservabilityMdcFilter(ObservabilityState.disabledState());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                new MockHttpServletRequest(),
                response,
                (servletRequest, servletResponse) -> {
                });

        assertThat(response.getHeader(ObservabilityMdcFilter.REQUEST_ID_HEADER)).isNull();
    }

    @Test
    void exposesOperationAndCorrelationFieldsDuringRequest() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ObservabilityMdcFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        ObservabilityMdcFilter filter = new ObservabilityMdcFilter(ObservabilityState.enabledState());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/agents/42");
        request.setAttribute(
                "org.springframework.web.servlet.HandlerMapping.bestMatchingPattern",
                "/agents/{id}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertThat(org.slf4j.MDC.get("operation")).isEqualTo("GET /agents/{id}");
            assertThat(org.slf4j.MDC.get("stage")).isEqualTo("inbound");
            assertThat(org.slf4j.MDC.get("request_id")).isNotBlank();
        });

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getMDCPropertyMap())
                .containsEntry("operation", "GET /agents/{id}")
                .containsEntry("status", "200")
                .containsKey("duration_ms");
        assertThat(org.slf4j.MDC.get("operation")).isNull();
        logger.detachAppender(appender);
    }

    @Test
    void errorRequestContainsErrorTypeAndStage() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ObservabilityMdcFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        ObservabilityMdcFilter filter = new ObservabilityMdcFilter(ObservabilityState.enabledState());

        try {
            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                    (servletRequest, servletResponse) -> {
                        throw new ServletException("expected");
                    });
        } catch (ServletException expected) {
            // Expected failure is part of the logging contract.
        }

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getMDCPropertyMap())
                .containsEntry("error.type", ServletException.class.getName())
                .containsEntry("error.stage", "servlet.filter");
        logger.detachAppender(appender);
    }
}
