package com.richie.component.web.core.spi.support;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MutableWebRequestContext} 字段语义 + 不可变防御 + 短路 + close 幂等测试。
 */
class MutableWebRequestContextTest {

    @Test
    void builder_basicGetters() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("POST")
                .path("/api/v1/users")
                .header("X-Api-Key", "abc")
                .header("Content-Type", "application/json")
                .queryParam("trace", "true")
                .pathVariable("id", "42")
                .build();

        assertThat(ctx.method()).isEqualTo("POST");
        assertThat(ctx.path()).isEqualTo("/api/v1/users");
        assertThat(ctx.header("x-api-key")).isEqualTo("abc"); // case-insensitive
        assertThat(ctx.headers("X-API-KEY")).containsExactly("abc");
        assertThat(ctx.queryParam("trace")).isEqualTo("true");
        assertThat(ctx.pathVariables()).containsEntry("id", "42");
    }

    @Test
    void method_isUppercased() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder().method("get").build();
        assertThat(ctx.method()).isEqualTo("GET");
    }

    @Test
    void headers_constructorInputIsDefensivelyCopied() {
        Map<String, List<String>> external = new HashMap<>();
        external.put("X-A", List.of("1"));

        MutableWebRequestContext ctx = new MutableWebRequestContext("GET", "/", external, Map.of());

        // mutate external after construction
        external.put("X-B", List.of("2"));

        // ctx should not see X-B (defensive copy)
        assertThat(ctx.headers("X-B")).isEmpty();
        assertThat(ctx.header("X-A")).isEqualTo("1");
    }

    @Test
    void nullHeaders_returnsEmptyList() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder().build();
        assertThat(ctx.headers("X-Nonexistent")).isEmpty();
        assertThat(ctx.header("X-Nonexistent")).isNull();
    }

    @Test
    void attributes_setGetRemove() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder().build();

        Object initial = ctx.attribute("user");
        assertThat(initial).isNull();
        ctx.setAttribute("user", "alice");
        Object set = ctx.attribute("user");
        assertThat(set).isEqualTo("alice");

        Object old = ctx.removeAttribute("user");
        assertThat(old).isEqualTo("alice");
        Object afterRemove = ctx.attribute("user");
        assertThat(afterRemove).isNull();
    }

    @Test
    void responseHeaders_writesOverwrite() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder().build();

        ctx.addResponseHeader("X-Trace-Id", "abc");
        ctx.addResponseHeader("X-Trace-Id", "def");

        assertThat(ctx.responseHeaders()).containsEntry("X-Trace-Id", "def");
    }

    @Test
    void markShortCircuit_setsStatusAndBodyAndFlag() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder().build();

        assertThat(ctx.isShortCircuited()).isFalse();
        ctx.markShortCircuit(429, "{\"error\":\"too many requests\"}");

        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(ctx.responseStatus()).isEqualTo(429);
        assertThat(ctx.shortCircuitBody()).contains("too many requests");
    }

    @Test
    void close_isIdempotent() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder().build();
        ctx.close();
        ctx.close(); // 不应抛
    }

    @Test
    void error_optionalWrapsNullable() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder().build();
        assertThat(ctx.error()).isEmpty();

        ctx.setError(new RuntimeException("boom"));
        assertThat(ctx.error()).hasValueSatisfying(t -> assertThat(t).hasMessage("boom"));
    }

    @Test
    void constructor_nullArgsRejected() {
        assertThatThrownBy(() -> new MutableWebRequestContext(null, "/", Map.of(), Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MutableWebRequestContext("GET", null, Map.of(), Map.of()))
                .isInstanceOf(NullPointerException.class);
    }
}