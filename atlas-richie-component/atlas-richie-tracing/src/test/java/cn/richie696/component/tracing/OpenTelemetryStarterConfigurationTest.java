package cn.richie696.component.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class OpenTelemetryStarterConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
            .withPropertyValues(
                    "otel.service.name=atlas-richie-tracing-test",
                    "otel.traces.exporter=none",
                    "otel.metrics.exporter=none",
                    "otel.logs.exporter=none");

    @Test
    void disabledSwitchExposesNoopOpenTelemetry() {
        contextRunner.withPropertyValues("otel.sdk.disabled=true").run(context -> {
            assertThat(context).hasSingleBean(OpenTelemetry.class);

            var span = context.getBean(OpenTelemetry.class)
                    .getTracer("test")
                    .spanBuilder("disabled")
                    .startSpan();
            try {
                assertThat(span.getSpanContext().isValid()).isFalse();
            } finally {
                span.end();
            }
        });
    }

    @Test
    void enabledSwitchCreatesSdkBackedSpans() {
        contextRunner.withPropertyValues("otel.sdk.disabled=false").run(context -> {
            assertThat(context).hasSingleBean(OpenTelemetry.class);

            var span = context.getBean(OpenTelemetry.class)
                    .getTracer("test")
                    .spanBuilder("enabled")
                    .startSpan();
            try {
                assertThat(span.getSpanContext().isValid()).isTrue();
            } finally {
                span.end();
            }
        });
    }
}
