package cn.richie696.component.observability.starter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

@EnabledIfEnvironmentVariable(named = "ATLAS_OBSERVABILITY_LOCAL_E2E", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = Phase2TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.application.name=atlas-observability-phase2-e2e",
                "spring.main.web-application-type=reactive",
                "otel.exporter.otlp.endpoint=http://127.0.0.1:4318",
                "otel.exporter.otlp.traces.endpoint=http://127.0.0.1:4318/v1/traces",
                "otel.exporter.otlp.metrics.endpoint=http://127.0.0.1:4318/v1/metrics",
                "otel.exporter.otlp.logs.endpoint=http://127.0.0.1:4318/v1/logs",
                "otel.exporter.otlp.protocol=http/protobuf",
                "otel.traces.exporter=otlp",
                "otel.metrics.exporter=otlp",
                "otel.logs.exporter=otlp",
                "otel.bsp.schedule.delay=1000",
                "otel.metric.export.interval=1000"
        })
class Phase2LocalOtlpSmokeTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void sendsApplicationSignalsToLocalAlloy() {
        try {
            WebTestClient.bindToApplicationContext(applicationContext)
                    .configureClient()
                    .responseTimeout(Duration.ofSeconds(5))
                    .build()
                    .get()
                    .uri("/phase2/trace")
                    .header("X-Request-Id", "phase2-local-e2e")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .consumeWith(response -> System.out.println(
                            "PHASE2_LOCAL_OTLP_BODY=" + new String(
                                    response.getResponseBodyContent(), java.nio.charset.StandardCharsets.UTF_8)))
                    .jsonPath("$.trace_id").isNotEmpty()
                    .jsonPath("$.span_id").isNotEmpty()
                    .jsonPath("$.request_id").isEqualTo("phase2-local-e2e");

            // Give the batch processors enough time to send the controlled smoke signal.
            Thread.sleep(2_500L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for local OTLP export", interrupted);
        }
    }
}
