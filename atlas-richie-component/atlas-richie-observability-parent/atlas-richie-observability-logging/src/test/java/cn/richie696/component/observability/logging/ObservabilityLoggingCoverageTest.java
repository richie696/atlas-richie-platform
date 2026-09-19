package cn.richie696.component.observability.logging;

import cn.richie696.component.observability.core.CorrelationIds;
import cn.richie696.component.observability.core.ObservabilityState;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityLoggingCoverageTest {

    @AfterEach
    void clearMdc() {
        org.slf4j.MDC.clear();
    }

    @Test
    void mdcScopeRestoresPreviousValuesAndRedactsSensitiveData() {
        org.slf4j.MDC.put("existing", "value");
        try (ObservabilityMdc.Scope ignored = ObservabilityMdc.install(
                new CorrelationIds("trace", "span", "request"))) {
            assertThat(org.slf4j.MDC.get("trace_id")).isEqualTo("trace");
            assertThat(org.slf4j.MDC.get("stage")).isEqualTo("inbound");
            ObservabilityMdc.put("token", "secret-token");
            ObservabilityMdc.put("operation", "hello\nworld");
            assertThat(org.slf4j.MDC.get("token")).isEqualTo("[REDACTED]");
            assertThat(org.slf4j.MDC.get("operation")).isEqualTo("hello_world");
        }
        assertThat(org.slf4j.MDC.get("existing")).isEqualTo("value");
        assertThat(ObservabilityMdc.safeValue(null, null)).isNull();
        try (ObservabilityMdc.Scope ignored = ObservabilityMdc.install(null)) {
            ObservabilityMdc.put("empty", "");
            assertThat(org.slf4j.MDC.get("empty")).isNull();
        }
    }

    @Test
    void autoConfigurationFactoriesExposeServletWebfluxAndReactorAdapters() {
        ObservabilityState state = ObservabilityState.enabledState();
        var servlet = new ObservabilityLoggingAutoConfiguration().observabilityMdcFilter(state);
        assertThat(servlet.getFilter()).isInstanceOf(ObservabilityMdcFilter.class);
        assertThat(servlet.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 100);
        WebFilter webFlux = new ObservabilityWebFluxLoggingAutoConfiguration()
                .observabilityWebFluxFilter(state);
        assertThat(webFlux).isInstanceOf(ObservabilityWebFluxFilter.class);

        var disabledInitializer = new ObservabilityReactorContextAutoConfiguration()
                .observabilityReactorContextInitializer(ObservabilityState.disabledState());
        disabledInitializer.afterSingletonsInstantiated();
        var enabledInitializer = new ObservabilityReactorContextAutoConfiguration()
                .observabilityReactorContextInitializer(state);
        enabledInitializer.afterSingletonsInstantiated();
    }

    @Test
    void logbackLifecycleTracksRunningStateWithoutRequiringSdkBean() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        SmartLifecycle lifecycle = new ObservabilityLogbackAutoConfiguration()
                .observabilityLogbackAppenderLifecycle(
                        factory.getBeanProvider(OpenTelemetry.class), ObservabilityState.enabledState());
        assertThat(lifecycle.isRunning()).isFalse();
        lifecycle.start();
        assertThat(lifecycle.isRunning()).isTrue();
        lifecycle.start();
        lifecycle.stop();
        assertThat(lifecycle.isRunning()).isFalse();

        SmartLifecycle disabled = new ObservabilityLogbackAutoConfiguration()
                .observabilityLogbackAppenderLifecycle(
                        factory.getBeanProvider(OpenTelemetry.class), ObservabilityState.disabledState());
        disabled.start();
        assertThat(disabled.isRunning()).isFalse();
    }

    @Test
    void webfluxFilterCompletesErrorAndPreservesRequestHeader() {
        var exchange = org.springframework.mock.web.server.MockServerWebExchange.from(
                org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/failure")
                        .header(ObservabilityMdcFilter.REQUEST_ID_HEADER, "request-error")
                        .build());
        try {
            new ObservabilityWebFluxFilter(ObservabilityState.enabledState())
                    .filter(exchange, ignored -> Mono.error(new IllegalStateException("expected")))
                    .block();
        } catch (IllegalStateException expected) {
            // error logging path is the contract under test
        }
        assertThat(exchange.getResponse().getHeaders()
                .getFirst(ObservabilityMdcFilter.REQUEST_ID_HEADER)).isEqualTo("request-error");
    }
}
