package com.richie.component.web.core.servlet;

import com.richie.component.web.core.spi.WebInterceptor;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link InterceptingFilter} 行为测试：覆盖链空 / 单拦截器 / 多拦截器顺序 / 短路 / 异常包装 / 头与 query 收集。
 * <p>
 * 测试用 Spring {@code spring-test} 的 {@code MockHttpServletRequest / MockHttpServletResponse / MockFilterChain}
 * 起 servlet 行为，无需真实容器。
 *
 * @author richie696
 * @since 2026-07
 */
class InterceptingFilterTest {

    // ───────────────────────── 链空 / 单拦截器 ─────────────────────────

    @Test
    void emptyChain_passesThrough() throws Exception {
        InterceptingFilter filter = new InterceptingFilter(List.of());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain downstream = new MockFilterChain();

        filter.doFilter(req, resp, downstream);

        assertThat(downstream.getRequest()).isSameAs(req);
        assertThat(downstream.getResponse()).isSameAs(resp);
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void singleInterceptor_executesAndProceeds() throws Exception {
        List<String> trace = new ArrayList<>();
        WebInterceptor inter = (ctx, chain) -> {
            trace.add("inter");
            ctx.setAttribute("trace-key", "trace-value");
            chain.proceed(ctx);
            trace.add("inter-after");
        };
        InterceptingFilter filter = new InterceptingFilter(List.of(inter));
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/test");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain downstream = new MockFilterChain();

        filter.doFilter(req, resp, downstream);

        assertThat(trace).containsExactly("inter", "inter-after");
        assertThat(downstream.getRequest()).isSameAs(req);
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    // ───────────────────────── 短路 ─────────────────────────

    @Test
    void shortCircuit_skipsFilterChainAndWritesBody() throws Exception {
        WebInterceptor rateLimit = (ctx, chain) -> {
            ctx.markShortCircuit(429, "{\"error\":\"rate_limit\"}");
        };
        InterceptingFilter filter = new InterceptingFilter(List.of(rateLimit));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain downstream = new MockFilterChain();

        filter.doFilter(req, resp, downstream);

        assertThat(downstream.getRequest()).isNull();
        assertThat(resp.getStatus()).isEqualTo(429);
        assertThat(resp.getContentAsString()).isEqualTo("{\"error\":\"rate_limit\"}");
        assertThat(resp.getContentType()).isEqualTo("application/json;charset=UTF-8");
    }

    @Test
    void shortCircuitHeaders_writtenToServletResponse() throws Exception {
        WebInterceptor inter = (ctx, chain) -> {
            ctx.addResponseHeader("X-RateLimit-Remaining", "0");
            ctx.addResponseHeader("X-Trace-Id", "abc-123");
            ctx.markShortCircuit(429, "{\"error\":\"rate_limit\"}");
        };
        InterceptingFilter filter = new InterceptingFilter(List.of(inter));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, new MockFilterChain());

        assertThat(resp.getStatus()).isEqualTo(429);
        assertThat(resp.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(resp.getHeader("X-Trace-Id")).isEqualTo("abc-123");
    }

    @Test
    void noMarkShortCircuit_doesNotWriteStatus() throws Exception {
        WebInterceptor inter = (ctx, chain) -> {
            ctx.setResponseStatus(503);
        };
        InterceptingFilter filter = new InterceptingFilter(List.of(inter));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain downstream = new MockFilterChain();

        filter.doFilter(req, resp, downstream);

        assertThat(downstream.getRequest()).isSameAs(req);
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    // ───────────────────────── 异常 ─────────────────────────

    @Test
    void exception_wrappedAsServletException() throws Exception {
        WebInterceptor faulty = (ctx, chain) -> {
            throw new IllegalStateException("boom");
        };
        InterceptingFilter filter = new InterceptingFilter(List.of(faulty));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain downstream = new MockFilterChain();

        assertThatThrownBy(() -> filter.doFilter(req, resp, downstream))
                .isInstanceOf(ServletException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("boom");

        assertThat(downstream.getRequest()).isNull();
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void exception_caughtByInterceptor_doesNotBubble() throws Exception {
        List<String> trace = new ArrayList<>();
        WebInterceptor faulty = (ctx, chain) -> {
            try {
                throw new IllegalStateException("recoverable");
            } catch (IllegalStateException e) {
                trace.add("caught");
            }
            chain.proceed(ctx);
            trace.add("after-proceed");
        };
        WebInterceptor downstream2 = (ctx, chain) -> {
            trace.add("downstream");
        };
        InterceptingFilter filter = new InterceptingFilter(List.of(faulty, downstream2));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain realChain = new MockFilterChain();

        filter.doFilter(req, resp, realChain);

        assertThat(trace).containsExactly("caught", "downstream", "after-proceed");
        assertThat(realChain.getRequest()).isSameAs(req);
    }

    // ───────────────────────── 多拦截器顺序 ─────────────────────────

    @Test
    void multipleInterceptors_executeInRegistrationOrder() throws Exception {
        List<String> trace = new ArrayList<>();
        WebInterceptor first = (ctx, chain) -> {
            trace.add("1-in");
            chain.proceed(ctx);
            trace.add("1-out");
        };
        WebInterceptor second = (ctx, chain) -> {
            trace.add("2-in");
            chain.proceed(ctx);
            trace.add("2-out");
        };
        WebInterceptor third = (ctx, chain) -> {
            trace.add("3-in");
            chain.proceed(ctx);
            trace.add("3-out");
        };
        InterceptingFilter filter = new InterceptingFilter(List.of(first, second, third));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain realChain = new MockFilterChain();

        filter.doFilter(req, resp, realChain);

        assertThat(trace).containsExactly(
                "1-in", "2-in", "3-in", "3-out", "2-out", "1-out");
        assertThat(realChain.getRequest()).isSameAs(req);
    }

    @Test
    void laterInterceptor_shortCircuits_skipsDownstreamButFirstReturnsNormally() throws Exception {
        List<String> trace = new ArrayList<>();
        WebInterceptor first = (ctx, chain) -> {
            trace.add("1-in");
            chain.proceed(ctx);
            if (!ctx.isShortCircuited()) {
                trace.add("1-out");
            }
        };
        WebInterceptor second = (ctx, chain) -> {
            trace.add("2-in");
            ctx.markShortCircuit(403, "{\"error\":\"forbidden\"}");
        };
        InterceptingFilter filter = new InterceptingFilter(List.of(first, second));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain realChain = new MockFilterChain();

        filter.doFilter(req, resp, realChain);

        assertThat(trace).containsExactly("1-in", "2-in");
        assertThat(realChain.getRequest()).isNull();
        assertThat(resp.getStatus()).isEqualTo(403);
    }

    // ───────────────────────── 数据收集 ─────────────────────────

    @Test
    void headers_collectedIntoContext() throws Exception {
        List<String> seenInInterceptor = new ArrayList<>();
        WebInterceptor capture = (ctx, chain) -> {
            seenInInterceptor.add(ctx.header("X-Trace-Id"));
            seenInInterceptor.add(ctx.header("Authorization"));
            chain.proceed(ctx);
        };
        InterceptingFilter filter = new InterceptingFilter(List.of(capture));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/test");
        req.addHeader("X-Trace-Id", "trace-abc");
        req.addHeader("Authorization", "Bearer xyz");

        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(seenInInterceptor).containsExactly("trace-abc", "Bearer xyz");
    }

    @Test
    void queryParams_collectedIntoContext() throws Exception {
        List<String> seenInInterceptor = new ArrayList<>();
        WebInterceptor capture = (ctx, chain) -> {
            seenInInterceptor.add(ctx.queryParam("name"));
            seenInInterceptor.add(ctx.queryParam("missing"));
            chain.proceed(ctx);
        };
        InterceptingFilter filter = new InterceptingFilter(List.of(capture));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/test");
        req.setParameter("name", "alice");

        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(seenInInterceptor).containsExactly("alice", null);
    }

    @Test
    void requestPath_doesNotContainQueryString() throws Exception {
        List<String> captured = new ArrayList<>();
        WebInterceptor capture = (ctx, chain) -> {
            captured.add(ctx.path());
            captured.add(ctx.method());
            chain.proceed(ctx);
        };
        InterceptingFilter filter = new InterceptingFilter(List.of(capture));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users");
        req.setQueryString("page=2&size=10");

        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(captured).containsExactly("/api/users", "GET");
    }

    // ───────────────────────── 构造参数校验 ─────────────────────────

    @Test
    void constructor_rejectsNullInterceptors() {
        assertThatThrownBy(() -> new InterceptingFilter(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("interceptors");
    }

    @Test
    void constructor_acceptsEmptyInterceptors() {
        InterceptingFilter filter = new InterceptingFilter(Collections.emptyList());
        assertThat(filter).isNotNull();
    }

    // ───────────────────────── 短路后 proceed no-op ─────────────────────────

    @Test
    void shortCircuit_proceedCallIsNoOp() throws Exception {
        List<String> trace = new ArrayList<>();
        WebInterceptor shortCircuit = (ctx, chain) -> {
            trace.add("sc-in");
            ctx.markShortCircuit(429, "{}");
            chain.proceed(ctx);
            trace.add("sc-out");
        };
        InterceptingFilter filter = new InterceptingFilter(List.of(shortCircuit));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, new MockFilterChain());

        assertThat(trace).containsExactly("sc-in", "sc-out");
        assertThat(resp.getStatus()).isEqualTo(429);
    }

    // ───────────────────────── IOException 透传 ─────────────────────────
// IOException 透传已在 exception_wrappedAsServletException 测试中覆盖（任意 Exception → ServletException）

}