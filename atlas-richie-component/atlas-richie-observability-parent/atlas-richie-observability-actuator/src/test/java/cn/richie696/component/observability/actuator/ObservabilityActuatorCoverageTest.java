package cn.richie696.component.observability.actuator;

import cn.richie696.component.observability.core.ObservabilityState;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservabilityActuatorCoverageTest {

    @Test
    void dependencyMetricsSupportsLazyRegistryAndSafeTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("registry", registry);
        DependencyMetrics metrics = new DependencyMetrics(
                beanFactory.getBeanProvider(MeterRegistry.class), ObservabilityState.enabledState());
        String longValue = "x".repeat(140) + "\n";
        metrics.recordRequest(null, longValue, "op\r", null, -10);
        metrics.recordConnection("", "", -3);

        assertThat(registry.get(DependencyMetrics.REQUESTS).timer().count()).isEqualTo(1);
        assertThat(registry.get(DependencyMetrics.CONNECTIONS).gauge().value()).isZero();
        assertThat(new DependencyMetrics((MeterRegistry) null, ObservabilityState.enabledState()))
                .isNotNull();
    }

    @Test
    void executorBinderHandlesSpringTaskExecutorAndUnknownExecutors() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        Executor unknown = Runnable::run;
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            new ExecutorMetricsBinder(Map.of("task", taskExecutor, "unknown", unknown),
                    ObservabilityState.enabledState()).bindTo(registry);
            assertThat(registry.find("executor.active").meter()).isNull();
        } finally {
            taskExecutor.shutdown();
        }
    }

    @Test
    void autoConfigurationCreatesFiltersBindersAndCommonTags() {
        ObservabilityActuatorAutoConfiguration configuration = new ObservabilityActuatorAutoConfiguration();
        assertThat(configuration.observabilityMeterFilter(ObservabilityState.enabledState())).isNotNull();
        assertThat(configuration.observabilityMeterFilter(ObservabilityState.disabledState())).isNotNull();

        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "spring.application.name", "atlas-app",
                "deployment.environment.name", "prod")));
        assertThat(configuration.observabilityCommonTags(environment)).isNotNull();

        StaticApplicationContext applicationContext = new StaticApplicationContext();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>());
        applicationContext.getBeanFactory().registerSingleton("executor", executor);
        try {
            assertThat(configuration.observabilityExecutorMetricsBinder(applicationContext,
                    new DefaultListableBeanFactory().getBeanProvider(
                            cn.richie696.component.observability.core.ObservabilityState.class))).isNotNull();
        } finally {
            executor.shutdownNow();
            applicationContext.close();
        }
    }

    @Test
    void rejectionHandlerCountsBeforeDelegating() {
        ObservabilityRejectedExecutionHandler handler = new ObservabilityRejectedExecutionHandler(
                new ThreadPoolExecutor.CallerRunsPolicy());
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>());
        try {
            handler.rejectedExecution(() -> { }, executor);
            assertThat(handler.rejectedCount()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
        assertThatThrownBy(() -> new ObservabilityRejectedExecutionHandler(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void otlpConfigurationBuildsFromStandardEnvironmentKeys() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "otel.exporter.otlp.metrics.endpoint", "http://collector:4318",
                "OTEL_METRIC_EXPORT_INTERVAL", "PT30S",
                "otel.resource.attributes", "service.version=1.0,invalid",
                "spring.application.name", "atlas-app")));
        var registry = new ObservabilityOtlpMetricsAutoConfiguration()
                .atlasRichieOtlpMeterRegistry(environment);
        try {
            assertThat(registry.config().clock()).isNotNull();
        } finally {
            registry.close();
        }
    }
}
