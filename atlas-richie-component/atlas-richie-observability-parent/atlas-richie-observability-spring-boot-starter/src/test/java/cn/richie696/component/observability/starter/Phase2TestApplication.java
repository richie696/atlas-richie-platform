package cn.richie696.component.observability.starter;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import io.opentelemetry.api.trace.Span;

import java.util.Map;

@SpringBootConfiguration
@EnableAutoConfiguration
@RestController
class Phase2TestApplication {

    @GetMapping("/phase2")
    Mono<String> phase2() {
        return Mono.just("phase2");
    }

    @GetMapping("/phase2/trace")
    Mono<Map<String, String>> trace() {
        String traceId = Span.current().getSpanContext().isValid()
                ? Span.current().getSpanContext().getTraceId()
                : "";
        String spanId = Span.current().getSpanContext().isValid()
                ? Span.current().getSpanContext().getSpanId()
                : "";
        return Mono.just(Map.of(
                "trace_id", traceId,
                "span_id", spanId,
                "request_id", org.slf4j.MDC.get("request_id")));
    }
}
