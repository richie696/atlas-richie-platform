package cn.richie696.component.observability.starter;

import cn.richie696.component.observability.core.ObservabilityState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = Phase2TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.application.name=phase2-enabled",
                "spring.main.web-application-type=reactive",
                "otel.traces.exporter=none",
                "otel.metrics.exporter=none",
                "otel.logs.exporter=none"
        })
class Phase2EnabledSpringBootTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ObservabilityState observabilityState;

    @Test
    void starterStartsWebFluxWithRequestIdAndSafeActuatorDefaults() {
        assertThat(observabilityState.enabled()).isTrue();
        WebTestClient webTestClient = WebTestClient.bindToApplicationContext(applicationContext)
                .build();

        webTestClient.get()
                .uri("/phase2")
                .header("X-Request-Id", "phase2-request")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Request-Id", "phase2-request")
                .expectBody(String.class).isEqualTo("phase2");

        webTestClient.get()
                .uri("/actuator/health/liveness")
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(response -> assertThat(response.getResponseBody())
                        .contains("jvm_memory_used_bytes"));
    }
}
