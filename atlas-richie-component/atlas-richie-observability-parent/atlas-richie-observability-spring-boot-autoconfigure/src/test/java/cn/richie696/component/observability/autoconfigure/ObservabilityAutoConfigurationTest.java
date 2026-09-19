package cn.richie696.component.observability.autoconfigure;

import cn.richie696.component.observability.core.ObservabilityState;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ObservabilityAutoConfiguration.class);

    @Test
    void bindsEnabledStateByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ObservabilityProperties.class);
            assertThat(context).hasSingleBean(ObservabilityState.class);
            assertThat(context.getBean(ObservabilityState.class).enabled()).isTrue();
        });
    }

    @Test
    void sdkDisabledMakesEffectiveStateDisabled() {
        contextRunner
                .withPropertyValues(
                        "atlas.observability.enabled=true",
                        "otel.sdk.disabled=true")
                .run(context -> assertThat(context.getBean(ObservabilityState.class).enabled())
                        .isFalse());
    }

    @Test
    void environmentPostProcessorAddsOfficialDisableProperties() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource("test", java.util.Map.of(
                        "atlas.observability.enabled", "false")));

        new ObservabilityEnvironmentPostProcessor()
                .postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("otel.sdk.disabled")).isEqualTo("true");
        assertThat(environment.getProperty("management.metrics.enable.all")).isEqualTo("false");
        assertThat(environment.getProperty("management.endpoint.prometheus.enabled"))
                .isEqualTo("false");
    }

    @Test
    void sdkDisabledAlsoClosesMetricsAndPrometheusEndpoints() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource("test", java.util.Map.of(
                        "otel.sdk.disabled", "true")));

        new ObservabilityEnvironmentPostProcessor()
                .postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("management.metrics.enable.all")).isEqualTo("false");
        assertThat(environment.getProperty("management.endpoint.prometheus.enabled"))
                .isEqualTo("false");
        assertThat(environment.getProperty("management.metrics.export.prometheus.enabled"))
                .isEqualTo("false");
    }

    @Test
    void sdkDisabledUsesAccessLevelWhenApplicationUsesBoot4EndpointContract() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource("test", java.util.Map.of(
                        "otel.sdk.disabled", "true",
                        "management.endpoint.prometheus.access", "unrestricted")));

        new ObservabilityEnvironmentPostProcessor()
                .postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("management.endpoint.prometheus.access"))
                .isEqualTo("none");
        assertThat(environment.getProperty("management.endpoint.prometheus.enabled"))
                .isNull();
    }

    @Test
    void environmentPostProcessorMapsPlatformResourceProperties() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource("test", java.util.Map.of(
                        "atlas.observability.service-namespace", "atlas-foundry",
                        "atlas.observability.criticality", "medium",
                        "otel.resource.attributes", "service.version=1.0.0")));

        new ObservabilityEnvironmentPostProcessor()
                .postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("otel.resource.attributes"))
                .isEqualTo("service.version=1.0.0,service.namespace=atlas-foundry,service.criticality=medium");
    }

    @Test
    void enabledModeExposesOnlySafeActuatorEndpointsByDefault() {
        MockEnvironment environment = new MockEnvironment();

        new ObservabilityEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info,prometheus");
        assertThat(environment.getProperty("management.endpoint.health.probes.enabled"))
                .isEqualTo("true");
    }

    @Test
    void endpointConfigurationIsNotOverriddenWhenApplicationProvidesIt() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", java.util.Map.of(
                "management.endpoints.web.exposure.include", "health,info",
                "management.endpoint.health.probes.enabled", "false")));

        new ObservabilityEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info");
        assertThat(environment.getProperty("management.endpoint.health.probes.enabled"))
                .isEqualTo("false");
    }

    @Test
    void unresolvedRemoteServiceNameFallsBackToSpringApplicationName() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", java.util.Map.of(
                "spring.application.name", "orchestrator-test",
                "otel.service.name", "${OTEL_SERVICE_NAME:${spring.application.name}}")));

        new ObservabilityEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("otel.service.name")).isEqualTo("orchestrator-test");
    }

    @Test
    void disabledOtelUsesOfficialNoopOpenTelemetry() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ObservabilityAutoConfiguration.class,
                        OpenTelemetryAutoConfiguration.class))
                .withPropertyValues(
                        "atlas.observability.enabled=false",
                        "otel.sdk.disabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenTelemetry.class);
                    assertThat(context.getBean(OpenTelemetry.class).getTracerProvider())
                            .isSameAs(io.opentelemetry.api.trace.TracerProvider.noop());
                });
    }

    @Test
    void officialOtelConfigurationToleratesUnresolvedRemoteServiceName() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ObservabilityAutoConfiguration.class,
                        OpenTelemetryAutoConfiguration.class))
                .withPropertyValues(
                        "spring.application.name=orchestrator-test",
                        "otel.service.name=${OTEL_SERVICE_NAME:${spring.application.name}}",
                        "otel.traces.exporter=none",
                        "otel.metrics.exporter=none",
                        "otel.logs.exporter=none")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void startupDiagnosticsDoNotExposeEndpointCredentials() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", java.util.Map.of(
                "spring.application.name", "diagnostic-test",
                "otel.resource.attributes", "service.version=1.0.0,authorization=secret",
                "otel.exporter.otlp.endpoint", "https://user:password@example.test:4318",
                "otel.traces.exporter", "otlp",
                "otel.metrics.exporter", "otlp",
                "otel.logs.exporter", "otlp",
                "otel.traces.sampler", "parentbased_always_on")));

        String line = new ObservabilityStartupDiagnostics(
                environment,
                ObservabilityState.enabledState(),
                OpenTelemetry.noop()).diagnosticLine();

        assertThat(line).contains("service.name=diagnostic-test")
                .contains("traces=otlp")
                .contains("sampler=parentbased_always_on")
                .doesNotContain("password")
                .doesNotContain("authorization=secret");
    }

    @Test
    void diagnosticsAndLifecycleBeansAreCreatedWithEffectiveState() {
        new ApplicationContextRunner()
                .withUserConfiguration(ObservabilityAutoConfiguration.class,
                        ObservabilityDiagnosticsAutoConfiguration.class)
                .withPropertyValues("otel.traces.exporter=none", "otel.metrics.exporter=none",
                        "otel.logs.exporter=none")
                .run(context -> {
                    assertThat(context).hasSingleBean(ObservabilityStartupDiagnostics.class);
                    assertThat(context).hasSingleBean(ObservabilityLifecycle.class);
                    context.getBean(ObservabilityStartupDiagnostics.class).onApplicationEvent(null);
                    context.getBean(ObservabilityStartupDiagnostics.class).onApplicationEvent(null);
                });
    }

    @Test
    void propagatorUsesOfficialOpenTelemetryBeanWhenPresent() {
        assertThat(new ObservabilityAutoConfiguration().observabilityPropagator(OpenTelemetry.noop()))
                .isNotNull();
    }

    @Test
    void lifecycleFlushesOfficialSdkAndIgnoresDisabledOrNoopImplementations() {
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().build();
        new ObservabilityLifecycle(ObservabilityState.enabledState(), sdk).onApplicationEvent(null);
        new ObservabilityLifecycle(ObservabilityState.disabledState(), sdk).onApplicationEvent(null);
        new ObservabilityLifecycle(ObservabilityState.enabledState(), OpenTelemetry.noop())
                .onApplicationEvent(null);
        sdk.close();
    }

    @Test
    void propertiesExposeAllPlatformSettings() {
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.setEnabled(false);
        properties.setServiceNamespace("atlas");
        properties.setCriticality("high");
        properties.setRedactionEnabled(false);
        properties.setMaxAttributeLength(128);

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getServiceNamespace()).isEqualTo("atlas");
        assertThat(properties.getCriticality()).isEqualTo("high");
        assertThat(properties.isRedactionEnabled()).isFalse();
        assertThat(properties.getMaxAttributeLength()).isEqualTo(128);
    }

    @Test
    void environmentPostProcessorUsesEnvironmentAndDefaultServiceNameFallbacks() {
        MockEnvironment fromEnv = new MockEnvironment();
        fromEnv.getPropertySources().addFirst(new MapPropertySource("test", java.util.Map.of(
                "otel.service.name", "${OTEL_SERVICE_NAME}",
                "OTEL_SERVICE_NAME", "env-service")));
        new ObservabilityEnvironmentPostProcessor().postProcessEnvironment(fromEnv, null);
        assertThat(fromEnv.getProperty("otel.service.name")).isEqualTo("env-service");

    }

}
