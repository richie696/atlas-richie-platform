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
                "spring.application.name=phase2-disabled",
                "spring.main.web-application-type=reactive",
                "atlas.observability.enabled=false"
        })
class Phase2DisabledSpringBootTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ObservabilityState observabilityState;

    @Test
    void switchKeepsHealthButRemovesObservationOutput() {
        assertThat(observabilityState.disabled()).isTrue();
        WebTestClient webTestClient = WebTestClient.bindToApplicationContext(applicationContext)
                .build();

        webTestClient.get()
                .uri("/phase2")
                .header("X-Request-Id", "disabled-request")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist("X-Request-Id")
                .expectBody(String.class).isEqualTo("phase2");

        webTestClient.get()
                .uri("/actuator/health/liveness")
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isNotFound();
    }
}
