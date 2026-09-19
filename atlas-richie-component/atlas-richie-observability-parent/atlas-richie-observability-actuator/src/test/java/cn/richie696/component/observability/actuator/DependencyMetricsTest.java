package cn.richie696.component.observability.actuator;

import cn.richie696.component.observability.core.ObservabilityState;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyMetricsTest {

    @Test
    void recordsLowCardinalityDependencyRequestAndConnectionMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DependencyMetrics metrics = new DependencyMetrics(
                registry, ObservabilityState.enabledState());

        metrics.recordRequest("http", "agent-service", "chat", "success", 1_000_000L);
        metrics.recordConnection("redis", "redis-primary", 3);

        assertThat(registry.get("dependency.request")
                .tag("dependency_type", "http")
                .tag("target_service", "agent-service")
                .tag("operation", "chat")
                .tag("status", "success")
                .timer().count()).isEqualTo(1);
        assertThat(registry.get("dependency.connection")
                .tag("dependency_type", "redis")
                .tag("target_service", "redis-primary")
                .gauge().value()).isEqualTo(3);
    }

    @Test
    void disabledStateDoesNotExportDependencyMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DependencyMetrics metrics = new DependencyMetrics(
                registry, ObservabilityState.disabledState());

        metrics.recordRequest("http", "agent-service", "chat", "success", 1_000_000L);
        metrics.recordConnection("redis", "redis-primary", 3);

        assertThat(registry.find("dependency.request").meter()).isNull();
        assertThat(registry.find("dependency.connection").meter()).isNull();
    }
}
