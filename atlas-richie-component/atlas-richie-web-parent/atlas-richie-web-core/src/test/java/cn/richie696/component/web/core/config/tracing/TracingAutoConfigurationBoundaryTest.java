package cn.richie696.component.web.core.config.tracing;

import cn.richie696.component.web.core.tracing.OtelTracingInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies that the new observability runtime owns the HTTP server boundary. */
class TracingAutoConfigurationBoundaryTest {

    @Test
    void unifiedObservabilityRuntimeDoesNotInstallLegacyTraceInterceptor() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TracingAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(OtelTracingInterceptor.class));
    }
}
