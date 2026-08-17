package cn.richie696.gateway.filter.common.infrastructure;

import cn.richie696.contract.constant.GlobalConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link RequestIdGlobalFilter} Wave 2 契约测试。
 * <p>
 * 覆盖五个传播点：
 * </p>
 * <ol>
 *   <li>ServerWebExchange attributes（{@code requestId} key）</li>
 *   <li>Reactor Context（{@code requestId} key）</li>
 *   <li>SLF4J MDC（{@code requestId} key）</li>
 *   <li>下游请求头（{@code X-Request-Id}）</li>
 *   <li>客户端响应头（{@code X-Request-Id}）</li>
 * </ol>
 *
 * @author richie696
 * @since 2026-08-04
 */
class RequestIdGlobalFilterTest {

    private RequestIdGlobalFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RequestIdGlobalFilter();
        chain = mock(GatewayFilterChain.class);
    }

    @Test
    @DisplayName("入口若无 X-Request-Id 则生成 32 位 hex")
    void testGenerateRequestId_WhenHeaderMissing() {
        // 准备：mock chain，捕获被装饰的 exchange
        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(inv -> {
            capturedExchange.set(inv.getArgument(0));
            return Mono.empty();
        });

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        // 执行
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        // 验证：exchange.attributes 中包含 32 位 hex
        ServerWebExchange decorated = capturedExchange.get();
        assertNotNull(decorated, "chain.filter 必须被调用，且 exchange 不为 null");
        String requestId = decorated.getAttribute(RequestIdGlobalFilter.ATTRIBUTE_KEY);
        assertNotNull(requestId, "ServerWebExchange.attributes 必须写入 requestId");
        assertEquals(32, requestId.length(), "requestId 应为 32 位");
        assertTrue(requestId.matches("[0-9a-f]{32}"),
                "requestId 应为 32 位小写十六进制字符串，实际：" + requestId);
    }

    @Test
    @DisplayName("入口若有 X-Request-Id 则沿用入参")
    void testPropagateIncomingRequestId() {
        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(inv -> {
            capturedExchange.set(inv.getArgument(0));
            return Mono.empty();
        });

        String incoming = "incoming-request-id-12345";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header(RequestIdGlobalFilter.HEADER_NAME, incoming)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        ServerWebExchange decorated = capturedExchange.get();
        assertEquals(incoming, decorated.getAttribute(RequestIdGlobalFilter.ATTRIBUTE_KEY),
                "应沿用入参 X-Request-Id");
        // 下游请求头也应携带
        assertEquals(incoming,
                decorated.getRequest().getHeaders().getFirst(RequestIdGlobalFilter.HEADER_NAME));
        // 响应头也应携带
        assertEquals(incoming,
                decorated.getResponse().getHeaders().getFirst(RequestIdGlobalFilter.HEADER_NAME));
    }

    @Test
    @DisplayName("下游请求 header 包含 X-Request-Id（被装饰的请求）")
    void testDownstreamRequestHeader() {
        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(inv -> {
            capturedExchange.set(inv.getArgument(0));
            return Mono.empty();
        });

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        // 验证：传入 chain 的 exchange.request.headers 中包含 X-Request-Id
        ServerWebExchange decorated = capturedExchange.get();
        HttpHeaders downstreamHeaders = decorated.getRequest().getHeaders();
        String downstreamRequestId = downstreamHeaders.getFirst(RequestIdGlobalFilter.HEADER_NAME);
        assertNotNull(downstreamRequestId, "下游请求 header 必须包含 X-Request-Id");
        assertEquals(32, downstreamRequestId.length());
    }

    @Test
    @DisplayName("响应 header 包含 X-Request-Id")
    void testResponseHeader() {
        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(inv -> {
            capturedExchange.set(inv.getArgument(0));
            return Mono.empty();
        });

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        // 验证：响应 header 中包含 X-Request-Id
        ServerWebExchange decorated = capturedExchange.get();
        MockServerHttpResponse response = (MockServerHttpResponse) decorated.getResponse();
        String responseRequestId = response.getHeaders().getFirst(RequestIdGlobalFilter.HEADER_NAME);
        assertNotNull(responseRequestId, "响应 header 必须包含 X-Request-Id");
    }

    @Test
    @DisplayName("Reactor Context 写入 requestId（可通过 contextWrite 链读取）")
    void testReactorContextWrite() {
        // 准备：在 chain.filter 内从 Context 中读取 requestId
        AtomicReference<String> contextRequestId = new AtomicReference<>();
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(inv -> {
            return Mono.deferContextual(ctx -> {
                contextRequestId.set(ctx.getOrDefault(
                        RequestIdGlobalFilter.CONTEXT_KEY, null));
                return Mono.<Void>empty();
            });
        });

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        // 验证：Reactor Context 应写入 requestId
        assertNotNull(contextRequestId.get(),
                "Reactor Context 中应写入 requestId");
        assertEquals(32, contextRequestId.get().length());
    }

    @Test
    @DisplayName("MDC 在 doFirst 中写入 requestId，在 doFinally 中清理")
    void testMdcWriteAndCleanup() {
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();

        // 契约：doFirst 在 chain.filter 返回的 Mono 订阅后触发 MDC 写入；
        // 因此 MDC 检查必须放在 chain Mono 内部（订阅后）而非 chain.filter 调用时。
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(inv -> {
            return Mono.fromRunnable(() -> {
                mdcDuringChain.set(MDC.get(RequestIdGlobalFilter.MDC_KEY));
            });
        });

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        MDC.clear();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        // 验证：chain Mono 运行时 MDC 中包含 requestId
        assertNotNull(mdcDuringChain.get(),
                "doFirst 应在 chain Mono 运行时写入 MDC");
        assertEquals(32, mdcDuringChain.get().length());

        // 验证：执行结束后 MDC 已清理
        assertNull(MDC.get(RequestIdGlobalFilter.MDC_KEY),
                "doFinally 应清理 MDC 中的 requestId");
    }

    @Test
    @DisplayName("Ordered 优先级为 HIGHEST_PRECEDENCE")
    void testOrderIsHighestPrecedence() {
        // 契约：filter 必须在所有过滤器之前执行，确保后续过滤器可读取 requestId
        assertEquals(Ordered.HIGHEST_PRECEDENCE, filter.getOrder(),
                "RequestIdGlobalFilter 必须是 HIGHEST_PRECEDENCE 优先级");
    }

    @Test
    @DisplayName("公开常量值校验（HEADER_NAME / ATTRIBUTE_KEY / CONTEXT_KEY / MDC_KEY）")
    void testPublicConstants() {
        assertEquals("X-Request-Id", RequestIdGlobalFilter.HEADER_NAME);
        assertEquals("requestId", RequestIdGlobalFilter.ATTRIBUTE_KEY);
        assertEquals("requestId", RequestIdGlobalFilter.CONTEXT_KEY);
        assertEquals("requestId", RequestIdGlobalFilter.MDC_KEY);
    }

    @Test
    @DisplayName("入口空字符串 X-Request-Id 被视为缺失，生成新 ID")
    void testBlankHeaderTreatedAsMissing() {
        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(inv -> {
            capturedExchange.set(inv.getArgument(0));
            return Mono.empty();
        });

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header(RequestIdGlobalFilter.HEADER_NAME, "   ")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        // 验证：空白 header 被视为缺失，生成新 32 位 ID
        String requestId = capturedExchange.get().getAttribute(RequestIdGlobalFilter.ATTRIBUTE_KEY);
        assertNotNull(requestId);
        assertEquals(32, requestId.length());
        assertTrue(requestId.matches("[0-9a-f]{32}"));
    }

    @Test
    @DisplayName("连续两次请求生成不同 ID（无状态副作用）")
    void testStatelessAcrossRequests() {
        AtomicReference<String> firstId = new AtomicReference<>();
        AtomicReference<String> secondId = new AtomicReference<>();

        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(inv -> {
            ServerWebExchange ex = inv.getArgument(0);
            if (firstId.get() == null) {
                firstId.set(ex.getAttribute(RequestIdGlobalFilter.ATTRIBUTE_KEY));
            } else if (secondId.get() == null) {
                secondId.set(ex.getAttribute(RequestIdGlobalFilter.ATTRIBUTE_KEY));
            }
            return Mono.empty();
        });

        // 第一次请求
        MockServerHttpRequest request1 = MockServerHttpRequest.get("/api/a").build();
        StepVerifier.create(filter.filter(MockServerWebExchange.from(request1), chain))
                .verifyComplete();
        // 第二次请求
        MockServerHttpRequest request2 = MockServerHttpRequest.get("/api/b").build();
        StepVerifier.create(filter.filter(MockServerWebExchange.from(request2), chain))
                .verifyComplete();

        assertNotNull(firstId.get());
        assertNotNull(secondId.get());
        assertTrue(!firstId.get().equals(secondId.get()),
                "连续两次请求必须生成不同的 requestId（无状态）");
    }

    @Test
    @DisplayName("X-RD-Request-Language 头不影响 requestId 逻辑")
    void testLanguageHeaderIndependent() {
        // 契约：requestId 仅依赖 X-Request-Id，与 i18n 头独立
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(inv -> {
            captured.set(inv.getArgument(0));
            return Mono.empty();
        });

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header(GlobalConstants.X_RD_REQUEST_LANGUAGE, "zh-CN")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        // X-RD-Request-Language 不会进入 requestId
        String requestId = captured.get().getAttribute(RequestIdGlobalFilter.ATTRIBUTE_KEY);
        assertNotNull(requestId);
        assertEquals(32, requestId.length());
    }
}

