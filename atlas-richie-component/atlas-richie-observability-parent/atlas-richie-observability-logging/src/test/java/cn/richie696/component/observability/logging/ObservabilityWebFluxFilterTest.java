package cn.richie696.component.observability.logging;

import cn.richie696.component.observability.core.ObservabilityState;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.integration.Slf4jThreadLocalAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityWebFluxFilterTest {

    private static final String ACCESSOR_KEY = "cp.slf4j";

    @BeforeEach
    void enableReactorContextPropagation() {
        ContextRegistry registry = ContextRegistry.getInstance();
        if (registry.getThreadLocalAccessors().stream()
                .noneMatch(accessor -> ACCESSOR_KEY.equals(accessor.key()))) {
            registry.registerThreadLocalAccessor(new Slf4jThreadLocalAccessor(
                    "trace_id", "span_id", "request_id", "operation", "stage",
                    "status", "duration_ms", "error.type", "error.stage"));
        }
        reactor.core.publisher.Hooks.enableAutomaticContextPropagation();
    }

    @AfterEach
    void clearMdc() {
        org.slf4j.MDC.clear();
    }

    @Test
    void propagatesRequestIdAcrossReactorSchedulerAndCleansMdc() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/agents/42")
                        .header(ObservabilityMdcFilter.REQUEST_ID_HEADER, "request-42")
                        .build());
        AtomicReference<String> requestIdSeenOnWorker = new AtomicReference<>();

        new ObservabilityWebFluxFilter(ObservabilityState.enabledState())
                .filter(exchange, ignored -> Mono.delay(Duration.ofMillis(5))
                        .publishOn(Schedulers.boundedElastic())
                        .then(Mono.fromRunnable(() -> requestIdSeenOnWorker
                                .set(org.slf4j.MDC.get("request_id")))))
                .block(Duration.ofSeconds(3));

        assertThat(exchange.getResponse().getHeaders()
                .getFirst(ObservabilityMdcFilter.REQUEST_ID_HEADER)).isEqualTo("request-42");
        assertThat(requestIdSeenOnWorker).hasValue("request-42");
        assertThat(org.slf4j.MDC.get("request_id")).isNull();
    }

    @Test
    void disabledStateDoesNotAddRequestIdOrMdc() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/health").build());

        new ObservabilityWebFluxFilter(ObservabilityState.disabledState())
                .filter(exchange, ignored -> Mono.fromRunnable(() -> {
                    assertThat(org.slf4j.MDC.get("request_id")).isNull();
                }))
                .block(Duration.ofSeconds(3));

        assertThat(exchange.getResponse().getHeaders()
                .getFirst(ObservabilityMdcFilter.REQUEST_ID_HEADER)).isNull();
    }
}
