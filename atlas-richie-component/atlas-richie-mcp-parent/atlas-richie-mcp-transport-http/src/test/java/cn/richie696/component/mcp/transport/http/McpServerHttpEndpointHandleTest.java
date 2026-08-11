package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpCompletionResult;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.api.server.McpCompletionHandler;
import cn.richie696.component.mcp.api.server.McpCompletionRequest;
import cn.richie696.component.mcp.protocol.McpMetaKeys;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.model.McpImplementationInfo;
import cn.richie696.component.mcp.server.completion.McpCompletionRegistry;
import cn.richie696.component.mcp.server.prompt.McpPromptRegistry;
import cn.richie696.component.mcp.server.resource.McpResourceRegistry;
import cn.richie696.component.mcp.server.tool.McpToolRegistration;
import cn.richie696.component.mcp.server.tool.McpToolRegistry;
import cn.richie696.component.mcp.server.tool.McpToolVisibilityPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpServerHttpEndpoint} 的统一入口 {@code handle}：
 * (1) 协议层校验失败映射为 {@link McpHttpResponse#json(int, Object)} + 400；
 * (2) 已知 method（{@code ping} / {@code tools/list} / {@code server/discover}）映射到标准 JSON-RPC 200 envelope；
 * (3) {@code id=null} 通知映射到 202；
 * (4) {@code notifications/cancelled} 反向写入 {@link McpCancellationRegistry}；
 * (5) {@code subscriptions/listen} 返回 SSE envelope；
 * (6) 未知 method 进入 default → 包 {@link McpProtocolException} 由 catch 分支
 *     翻译为 HTTP 400 + JSON-RPC error envelope；
 * (7) {@link CompletionException} 包裹的协议异常被识别为 400；
 * (8) 工具执行抛普通异常时映射为 500 + {@code MCP_INTERNAL_ERROR}。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpServerHttpEndpoint 端点 handle")
class McpServerHttpEndpointHandleTest {

    @Test
    @DisplayName("ping：返回 resultType=complete 的最简 envelope")
    void pingReturnsMinimalCompletion() {
        McpServerHttpEndpoint endpoint = newEndpoint(emptyRegistry());

        McpHttpResponse response = invoke(endpoint,
                body("ping", 1, Map.of()),
                validHeadersFor("ping"));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo("application/json");
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) response.body();
        assertThat(envelope).containsEntry("jsonrpc", "2.0").containsEntry("id", 1);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) envelope.get("result");
        assertThat(result).containsEntry("resultType", "complete");
    }

    @Test
    @DisplayName("server/discover：响应包含 supportedVersions + serverInfo（嵌在 _meta）")
    void discoverIncludesCapabilities() {
        McpServerHttpEndpoint endpoint = newEndpoint(emptyRegistry());

        McpHttpResponse response = invoke(endpoint,
                body("server/discover", 7, Map.of()),
                validHeadersFor("server/discover"));

        assertThat(response.status()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) response.body();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) envelope.get("result");
        assertThat(result).containsKeys("supportedVersions", "capabilities", "ttlMs", "cacheScope");
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) result.get("_meta");
        @SuppressWarnings("unchecked")
        Map<String, Object> serverInfo = (Map<String, Object>) meta
                .get("io.modelcontextprotocol/serverInfo");
        assertThat(serverInfo).containsEntry("name", "atlas-richie-test");
    }

    @Test
    @DisplayName("tools/list：空 registry 返回空 tools 数组")
    void toolsListReturnsEmptyArrayWhenEmpty() {
        McpServerHttpEndpoint endpoint = newEndpoint(emptyRegistry());

        McpHttpResponse response = invoke(endpoint,
                body("tools/list", 2, Map.of()),
                validHeadersFor("tools/list"));

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) response.body();
        @SuppressWarnings("unchecked")
        Map<String, Object> page = (Map<String, Object>) envelope.get("result");
        assertThat((List<?>) page.get("tools")).isEmpty();
    }

    @Test
    @DisplayName("tools/list：注册的 tool 进入返回列表")
    void toolsListIncludesRegisteredTools() {
        McpToolRegistry registry = new McpToolRegistry();
        registry.register(new McpToolRegistration(
                new McpToolDescriptor(
                        "echo", null, null,
                        Map.of("type", "object"), Map.of(), Map.of()),
                (args, ctx) -> CompletableFuture.completedFuture(
                        new McpToolResponse(List.of(), Map.of(), false))));
        McpServerHttpEndpoint endpoint = newEndpoint(registry);

        McpHttpResponse response = invoke(endpoint,
                body("tools/list", 3, Map.of()),
                validHeadersFor("tools/list"));

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) response.body();
        @SuppressWarnings("unchecked")
        Map<String, Object> page = (Map<String, Object>) envelope.get("result");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) page.get("tools");
        assertThat(tools).hasSize(1);
        assertThat(tools.getFirst()).containsEntry("name", "echo");
    }

    @Test
    @DisplayName("通知请求（id 缺失）：返回 202 Accepted")
    void notificationReturnsAccepted() {
        McpServerHttpEndpoint endpoint = newEndpoint(emptyRegistry());

        String body = """
                {"jsonrpc":"2.0","method":"tools/list","params":{"_meta":{
                  "io.modelcontextprotocol/protocolVersion":"2026-07-28",
                  "io.modelcontextprotocol/clientCapabilities":{}}}}
                """;
        McpHttpResponse response = invoke(endpoint, body, validHeadersFor("tools/list"));

        assertThat(response.status()).isEqualTo(202);
        assertThat(response.body()).isNull();
        assertThat(response.contentType()).isNull();
    }

    @Test
    @DisplayName("notifications/cancelled：未知 requestId 也安全返回 202")
    void cancelledNotificationOnUnknownIdIsSafe() {
        McpServerHttpEndpoint endpoint = newEndpoint(emptyRegistry());

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("requestId", "never-registered");
        params.put("_meta", defaultMeta());
        String body = """
                {"jsonrpc":"2.0","method":"notifications/cancelled","params":%s}
                """.formatted(toJson(params));
        McpHttpResponse response = invoke(endpoint, body, validHeadersFor("notifications/cancelled"));

        assertThat(response.status()).isEqualTo(202);
        assertThat(response.body()).isNull();
    }

    @Test
    @DisplayName("subscriptions/listen：返回 SSE 响应携带 Subscription publisher 与 acknowledged 通知")
    void subscriptionsListenReturnsSse() {
        McpServerHttpEndpoint endpoint = newEndpoint(emptyRegistry());

        Map<String, Object> notifications = Map.of(
                "toolsListChanged", true,
                "resourcesListChanged", false,
                "promptsListChanged", false,
                "resourceSubscriptions", List.of());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("notifications", notifications);
        params.put("_meta", defaultMeta());
        String body = """
                {"jsonrpc":"2.0","id":99,"method":"subscriptions/listen","params":%s}
                """.formatted(toJson(params));

        McpHttpResponse response = invoke(endpoint, body, validHeadersFor("subscriptions/listen"));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo("text/event-stream");
        assertThat(response.body()).isInstanceOf(McpSubscriptionManager.Subscription.class);
        assertThat(response.notifications()).hasSize(1);
        assertThat(response.notifications().getFirst()).containsEntry(
                "method", "notifications/subscriptions/acknowledged");
    }

    @Test
    @DisplayName("未知 method：返回 404 + -32601 method not found 错误 envelope")
    void unknownMethodReturnsProtocolError() {
        McpServerHttpEndpoint endpoint = newEndpoint(emptyRegistry());

        McpHttpResponse response = invoke(endpoint,
                body("mystery/method", 1, Map.of()),
                validHeadersFor("mystery/method"));

        assertThat(response.status()).isEqualTo(404);
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) response.body();
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) envelope.get("error");
        assertThat(error).containsEntry("code", -32601);
    }

    @Test
    @DisplayName("completion/complete 未开启：返回 404 -32601")
    void completionWithoutRegistryFailsWithMethodNotFound() {
        McpServerHttpEndpoint endpoint = newEndpoint(emptyRegistry());

        McpHttpResponse response = invoke(endpoint,
                body("completion/complete", 1, Map.of(
                        "ref", Map.of("type", "ref/prompt"),
                        "argument", Map.of("name", "x", "value", "y"))),
                validHeadersFor("completion/complete"));

        assertThat(response.status()).isEqualTo(404);
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) response.body();
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) envelope.get("error");
        assertThat(error).containsEntry("code", -32601);
    }

    @Test
    @DisplayName("completion/complete 缺少参数：返回 400 -32602 invalid params")
    void completionWithMissingArgumentsInvalidParams() {
        McpCompletionHandler handler = new McpCompletionHandler() {
            @Override
            public CompletableFuture<McpCompletionResult> complete(
                    McpCompletionRequest request, McpCallContext context) {
                return CompletableFuture.completedFuture(
                        new McpCompletionResult(List.of(), null, false));
            }
        };
        McpServerHttpEndpoint endpoint = newEndpointWithCompletionRegistry(
                emptyRegistry(),
                new McpCompletionRegistry(handler));

        McpHttpResponse response = invoke(endpoint,
                body("completion/complete", 1, Map.of(
                        "ref", Map.of("type", "ref/prompt"),
                        "argument", Map.of("name", "x"))),
                validHeadersFor("completion/complete"));

        assertThat(response.status()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) response.body();
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) envelope.get("error");
        assertThat(error).containsEntry("code", -32602);
    }

    @Test
    @DisplayName("tools/call 抛 CompletionException 包含协议异常：返回 400")
    void completionExceptionWrappingProtocolIs400() {
        McpToolRegistry registry = new McpToolRegistry();
        registry.register(new McpToolRegistration(
                new McpToolDescriptor(
                        "boom", null, null,
                        Map.of("type", "object"), Map.of(), Map.of()),
                (args, ctx) -> CompletableFuture.failedFuture(
                        new CompletionException(
                                new McpProtocolException("E", -32602, "bad args", Map.of())))));
        McpServerHttpEndpoint endpoint = newEndpoint(registry);

        McpHttpResponse response = invoke(endpoint,
                body("tools/call", 5, Map.of("name", "boom", "arguments", Map.of())),
                toolCallHeaders("boom"));

        assertThat(response.status()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) response.body();
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) envelope.get("error");
        assertThat(error).containsEntry("code", -32602);
    }

    @Test
    @DisplayName("tools/call 抛普通 Exception：被 dispatcher 包为协议错误并返回 400 -32603")
    void genericExceptionBecomesInternalError() {
        McpToolRegistry registry = new McpToolRegistry();
        registry.register(new McpToolRegistration(
                new McpToolDescriptor(
                        "explode", null, null,
                        Map.of("type", "object"), Map.of(), Map.of()),
                (args, ctx) -> CompletableFuture.failedFuture(new IllegalStateException("oops"))));
        McpServerHttpEndpoint endpoint = newEndpoint(registry);

        McpHttpResponse response = invoke(endpoint,
                body("tools/call", 6, Map.of("name", "explode", "arguments", Map.of())),
                toolCallHeaders("explode"));

        assertThat(response.status()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) response.body();
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) envelope.get("error");
        assertThat(error).containsEntry("code", -32603);
    }

    @Test
    @DisplayName("协议版本校验失败（不一致）：映射为 400")
    void invalidProtocolVersionMapsToTransportError() {
        McpServerHttpEndpoint endpoint = newEndpoint(emptyRegistry());

        Map<String, List<String>> headers = validHeadersFor("ping");
        headers.put("MCP-Protocol-Version", List.of("2025-11-25"));
        McpHttpResponse response = invoke(endpoint,
                body("ping", 1, Map.of()),
                headers);

        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("构造器：关键参数为 null 抛 NPE")
    void constructorNullChecks() {
        assertThatThrownBy(() -> new McpServerHttpEndpoint(
                null, new McpImplementationInfo("x", "1")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new McpServerHttpEndpoint(
                new McpToolRegistry(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("构造器未指定 originPolicy 时默认通过所有 origin")
    void defaultOriginPolicyAcceptsAll() {
        McpServerHttpEndpoint endpoint = new McpServerHttpEndpoint(
                emptyRegistry(), new McpImplementationInfo("test", "1.0"));
        Map<String, List<String>> headers = validHeadersFor("ping");
        headers.put("Origin", List.of("https://anywhere.example"));

        McpHttpResponse response = invoke(endpoint, body("ping", 1, Map.of()), headers);

        assertThat(response.status()).isEqualTo(200);
    }

    @Test
    @DisplayName("subscriptionManager getter 返回非空 manager")
    void subscriptionManagerAccessorReturnsManager() {
        McpServerHttpEndpoint endpoint = newEndpoint(emptyRegistry());
        assertThat(endpoint.subscriptionManager()).isNotNull();
        assertThat(endpoint.subscriptionManager().open(
                        "sub-1", new McpSubscriptionSpec(true, false, false, Set.of())))
                .isNotNull();
    }

    // ---- helpers ----

    private static McpToolRegistry emptyRegistry() {
        return new McpToolRegistry(McpToolVisibilityPolicy.ALLOW_ALL);
    }

    private static McpServerHttpEndpoint newEndpoint(McpToolRegistry registry) {
        return new McpServerHttpEndpoint(
                registry,
                new McpImplementationInfo("atlas-richie-test", "1.0"),
                origin -> true,
                new McpResourceRegistry(),
                new McpPromptRegistry());
    }

    private static McpServerHttpEndpoint newEndpointWithCompletionRegistry(
            McpToolRegistry registry,
            McpCompletionRegistry completionRegistry) {
        return new McpServerHttpEndpoint(
                registry,
                new McpImplementationInfo("atlas-richie-test", "1.0"),
                origin -> true,
                new McpResourceRegistry(),
                new McpPromptRegistry(),
                completionRegistry);
    }

    private static Map<String, List<String>> validHeaders() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", List.of("application/json; charset=utf-8"));
        headers.put("Accept", List.of("application/json, text/event-stream"));
        headers.put("MCP-Protocol-Version", List.of(McpProtocolVersions.V_2026_07_28));
        headers.put("Mcp-Method", List.of("ping"));
        return headers;
    }

    private static String body(String method, Object id, Map<String, Object> params) {
        return "{\"jsonrpc\":\"2.0\"," +
                (id != null ? "\"id\":" + (id instanceof String ? "\"" + id + "\"" : id) + "," : "") +
                "\"method\":\"" + method + "\"," +
                "\"params\":" + toJson(paramsWithMeta(params)) + "}";
    }

    private static Map<String, List<String>> validHeadersFor(String method) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", List.of("application/json; charset=utf-8"));
        headers.put("Accept", List.of("application/json, text/event-stream"));
        headers.put("MCP-Protocol-Version", List.of(McpProtocolVersions.V_2026_07_28));
        headers.put("Mcp-Method", List.of(method));
        return headers;
    }

    private static Map<String, List<String>> toolCallHeaders(String toolName) {
        Map<String, List<String>> headers = validHeadersFor("tools/call");
        headers.put("Mcp-Name", List.of(toolName));
        return headers;
    }

    private static Map<String, Object> paramsWithMeta(Map<String, Object> params) {
        Map<String, Object> wrapped = new LinkedHashMap<>(params);
        wrapped.put("_meta", defaultMeta());
        return wrapped;
    }

    private static Map<String, Object> defaultMeta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put(McpMetaKeys.PROTOCOL_VERSION, McpProtocolVersions.V_2026_07_28);
        meta.put(McpMetaKeys.CLIENT_CAPABILITIES, Map.of());
        return meta;
    }

    private static McpHttpResponse invoke(
            McpServerHttpEndpoint endpoint,
            String body,
            Map<String, List<String>> headers) {
        return endpoint.handle(body, headers);
    }

    private static String toJson(Object value) {
        return tools.jackson.databind.json.JsonMapper.builder().build()
                .writeValueAsString(value);
    }
}
