package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.McpProgressReporter;
import cn.richie696.component.mcp.api.model.McpCompletionResult;
import cn.richie696.component.mcp.api.model.McpPromptContent;
import cn.richie696.component.mcp.api.model.McpPromptDescriptor;
import cn.richie696.component.mcp.api.model.McpResourceContent;
import cn.richie696.component.mcp.api.model.McpResourceDescriptor;
import cn.richie696.component.mcp.api.model.McpResourceTemplateDescriptor;
import cn.richie696.component.mcp.api.server.McpCompletionHandler;
import cn.richie696.component.mcp.api.server.McpCompletionRequest;
import cn.richie696.component.mcp.api.server.McpPromptHandler;
import cn.richie696.component.mcp.api.server.McpResourceHandler;
import cn.richie696.component.mcp.protocol.McpMetaKeys;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.model.McpImplementationInfo;
import cn.richie696.component.mcp.server.completion.McpCompletionRegistry;
import cn.richie696.component.mcp.server.prompt.McpPromptRegistration;
import cn.richie696.component.mcp.server.prompt.McpPromptRegistry;
import cn.richie696.component.mcp.server.resource.McpResourceRegistration;
import cn.richie696.component.mcp.server.resource.McpResourceRegistry;
import cn.richie696.component.mcp.server.resource.McpResourceTemplateRegistration;
import cn.richie696.component.mcp.server.tool.McpToolRegistry;
import cn.richie696.component.mcp.server.tool.McpToolVisibilityPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 补全 {@link McpServerHttpEndpoint} 的覆盖：{@code resources/list} /
 * {@code resources/templates/list} / {@code resources/read} / {@code prompts/list} /
 * {@code prompts/get} / {@code completion/complete} 正常路径，以及
 * {@link McpServerHttpEndpoint.handle} 上的 progress 报告（{@code progressToken}
 * 触发的 {@code ProgressCollector} 路径）。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpServerHttpEndpoint 端点 handle 补全")
class McpServerHttpEndpointHandleExtraTest {

    @Test
    @DisplayName("resources/list：注册资源进入返回列表")
    void resourcesListReturnsRegisteredEntries() {
        McpToolRegistry tools = newToolRegistry();
        McpResourceRegistry resources = new McpResourceRegistry();
        McpPromptRegistry prompts = new McpPromptRegistry();
        McpServerHttpEndpoint endpoint = newEndpoint(tools, resources, prompts);
        resources.register(new McpResourceRegistration(
                new McpResourceDescriptor(
                        "file://a", "a", "A", "doc", "text/plain",
                        100L, List.of(), Map.of()),
                (uri, ctx) -> CompletableFuture.completedFuture(
                        new McpResourceContent(List.of(Map.of("uri", uri, "text", "x"))))));

        McpHttpResponse response = endpoint.handle(
                body("resources/list", 1, Map.of()),
                validHeadersFor("resources/list"));

        assertThat(response.status()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) response.body();
        @SuppressWarnings("unchecked")
        Map<String, Object> page = (Map<String, Object>) envelope.get("result");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) page.get("resources");
        assertThat(list).hasSize(1);
        assertThat(list.getFirst()).containsEntry("uri", "file://a");
    }

    @Test
    @DisplayName("resources/templates/list：返回模板列表")
    void resourceTemplatesListReturnsRegisteredTemplates() {
        McpToolRegistry tools = newToolRegistry();
        McpResourceRegistry resources = new McpResourceRegistry();
        McpPromptRegistry prompts = new McpPromptRegistry();
        McpServerHttpEndpoint endpoint = newEndpoint(tools, resources, prompts);
        resources.registerTemplate(new McpResourceTemplateRegistration(
                new McpResourceTemplateDescriptor(
                        "file:///{name}", "tpl", null, null, null, List.of(), Map.of()),
                (uri, ctx) -> CompletableFuture.completedFuture(
                        new McpResourceContent(List.of()))));

        McpHttpResponse response = endpoint.handle(
                body("resources/templates/list", 2, Map.of()),
                validHeadersFor("resources/templates/list"));

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) response.body();
        @SuppressWarnings("unchecked")
        Map<String, Object> page = (Map<String, Object>) envelope.get("result");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> templates = (List<Map<String, Object>>) page.get("resourceTemplates");
        assertThat(templates).hasSize(1);
        assertThat(templates.getFirst()).containsEntry("uriTemplate", "file:///{name}");
    }

    @Test
    @DisplayName("resources/read：返回 contents")
    void resourcesReadReturnsContents() {
        McpToolRegistry tools = newToolRegistry();
        McpResourceRegistry resources = new McpResourceRegistry();
        McpPromptRegistry prompts = new McpPromptRegistry();
        McpResourceHandler handler = new McpResourceHandler() {
            @Override
            public CompletableFuture<McpResourceContent> read(
                    String uri, McpCallContext context) {
                return CompletableFuture.completedFuture(
                        new McpResourceContent(List.of(Map.of("uri", uri, "text", "hi"))));
            }
        };
        resources.register(new McpResourceRegistration(
                new McpResourceDescriptor(
                        "file://a", "a", null, null, null, null, List.of(), Map.of()),
                handler));
        McpServerHttpEndpoint endpoint = newEndpoint(tools, resources, prompts);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("uri", "file://a");
        params.put("_meta", defaultMeta());
        McpHttpResponse response = endpoint.handle(
                body("resources/read", 3, params),
                resourceReadHeaders("file://a"));

        assertThat(response.status()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) response.body();
        assertThat(envelope).containsKey("result");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) envelope.get("result");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contents = (List<Map<String, Object>>) result.get("contents");
        assertThat(contents).hasSize(1);
        assertThat(contents.getFirst()).containsEntry("uri", "file://a");
    }

    @Test
    @DisplayName("prompts/list：返回模板列表")
    void promptsListReturnsRegisteredEntries() {
        McpToolRegistry tools = newToolRegistry();
        McpResourceRegistry resources = new McpResourceRegistry();
        McpPromptRegistry prompts = new McpPromptRegistry();
        McpPromptHandler handler = new McpPromptHandler() {
            @Override
            public CompletableFuture<McpPromptContent> get(
                    Map<String, Object> arguments, McpCallContext context) {
                return CompletableFuture.completedFuture(
                        new McpPromptContent("", List.of()));
            }
        };
        McpServerHttpEndpoint endpoint = newEndpoint(tools, resources, prompts);
        prompts.register(new McpPromptRegistration(
                new McpPromptDescriptor("greet", null, "d", List.of()),
                handler));

        McpHttpResponse response = endpoint.handle(
                body("prompts/list", 1, Map.of()),
                validHeadersFor("prompts/list"));

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) response.body();
        @SuppressWarnings("unchecked")
        Map<String, Object> page = (Map<String, Object>) envelope.get("result");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) page.get("prompts");
        assertThat(list).hasSize(1);
        assertThat(list.getFirst()).containsEntry("name", "greet");
    }

    @Test
    @DisplayName("prompts/get：调用 handler 并返回 messages")
    void promptsGetRendersMessages() {
        McpToolRegistry tools = newToolRegistry();
        McpResourceRegistry resources = new McpResourceRegistry();
        McpPromptRegistry prompts = new McpPromptRegistry();
        McpPromptHandler handler = new McpPromptHandler() {
            @Override
            public CompletableFuture<McpPromptContent> get(
                    Map<String, Object> arguments, McpCallContext context) {
                return CompletableFuture.completedFuture(
                        new McpPromptContent("ok", List.of(
                                Map.of("role", "user", "content", Map.of("text", "hi")))));
            }
        };
        McpServerHttpEndpoint endpoint = newEndpoint(tools, resources, prompts);
        prompts.register(new McpPromptRegistration(
                new McpPromptDescriptor("greet", null, null, List.of()),
                handler));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "greet");
        params.put("arguments", Map.of("lang", "en"));
        params.put("_meta", defaultMeta());
        McpHttpResponse response = endpoint.handle(
                body("prompts/get", 2, params),
                promptsGetHeaders("greet"));

        assertThat(response.status()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) response.body();
        assertThat(envelope).containsKey("result");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) envelope.get("result");
        assertThat(result).containsEntry("description", "ok");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) result.get("messages");
        assertThat(messages).hasSize(1);
    }

    @Test
    @DisplayName("completion/complete：handler 被调用并返回候选列表")
    void completionHandlerIsInvoked() {
        McpToolRegistry tools = newToolRegistry();
        McpCompletionHandler handler = new McpCompletionHandler() {
            @Override
            public CompletableFuture<McpCompletionResult> complete(
                    McpCompletionRequest request, McpCallContext context) {
                return CompletableFuture.completedFuture(
                        new McpCompletionResult(List.of("alpha", "beta"), 2, false));
            }
        };
        McpServerHttpEndpoint endpoint = newEndpointWithCompletionRegistry(
                tools, new McpCompletionRegistry(handler));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ref", Map.of("type", "ref/prompt"));
        params.put("argument", Map.of("name", "x", "value", "y"));
        params.put("_meta", defaultMeta());
        McpHttpResponse response = endpoint.handle(
                body("completion/complete", 1, params),
                validHeadersFor("completion/complete"));

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) response.body();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) envelope.get("result");
        assertThat(result).containsEntry("resultType", "complete");
        @SuppressWarnings("unchecked")
        Map<String, Object> completion = (Map<String, Object>) result.get("completion");
        @SuppressWarnings("unchecked")
        List<String> values = (List<String>) completion.get("values");
        assertThat(values).containsExactly("alpha", "beta");
        assertThat(completion).containsEntry("total", 2);
    }

    @Test
    @DisplayName("tools/call 触发 progressToken：report 累积到 notifications 列表")
    void progressReporterCollectsNotifications() {
        McpToolRegistry tools = newToolRegistry();
        tools.register(new cn.richie696.component.mcp.server.tool.McpToolRegistration(
                new cn.richie696.component.mcp.api.model.McpToolDescriptor(
                        "long-task", null, null,
                        Map.of("type", "object"), Map.of(), Map.of()),
                (args, ctx) -> {
                    McpProgressReporter reporter = ctx.progressReporter();
                    reporter.report(0.5, 1.0, "halfway");
                    return CompletableFuture.completedFuture(
                            new cn.richie696.component.mcp.api.model.McpToolResponse(
                                    List.of(), Map.of(), false));
                }));
        McpServerHttpEndpoint endpoint = newEndpoint(
                tools, new McpResourceRegistry(), new McpPromptRegistry());

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "long-task");
        params.put("arguments", Map.of());
        params.put("progressToken", "tok-1");
        params.put("_meta", defaultMeta());
        McpHttpResponse response = endpoint.handle(
                body("tools/call", 9, params),
                toolCallHeaders("long-task"));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.notifications()).isNotEmpty();
        Map<String, Object> notification = response.notifications().getFirst();
        assertThat(notification).containsEntry("method", "notifications/progress");
    }

    @Test
    @DisplayName("ProgressCollector：非单调递增被 dispatcher 包为工具错误")
    void progressReporterRejectsDecreasingProgress() {
        McpToolRegistry tools = newToolRegistry();
        tools.register(new cn.richie696.component.mcp.server.tool.McpToolRegistration(
                new cn.richie696.component.mcp.api.model.McpToolDescriptor(
                        "broken-task", null, null,
                        Map.of("type", "object"), Map.of(), Map.of()),
                (args, ctx) -> {
                    McpProgressReporter reporter = ctx.progressReporter();
                    reporter.report(0.8, 1.0, "up");
                    reporter.report(0.2, 1.0, "down");
                    return CompletableFuture.completedFuture(
                            new cn.richie696.component.mcp.api.model.McpToolResponse(
                                    List.of(), Map.of(), false));
                }));
        McpServerHttpEndpoint endpoint = newEndpoint(
                tools, new McpResourceRegistry(), new McpPromptRegistry());

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "broken-task");
        params.put("arguments", Map.of());
        params.put("progressToken", "tok-2");
        params.put("_meta", defaultMeta());
        McpHttpResponse response = endpoint.handle(
                body("tools/call", 10, params),
                toolCallHeaders("broken-task"));

        assertThat(response.status()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) response.body();
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) envelope.get("error");
        assertThat(error.get("message").toString()).contains("Tool execution failed");
    }

    @Test
    @DisplayName("ProgressCollector：NaN/Infinity 触发有限性校验")
    void progressReporterRejectsNonFiniteValues() {
        McpToolRegistry tools = newToolRegistry();
        tools.register(new cn.richie696.component.mcp.server.tool.McpToolRegistration(
                new cn.richie696.component.mcp.api.model.McpToolDescriptor(
                        "nan-task", null, null,
                        Map.of("type", "object"), Map.of(), Map.of()),
                (args, ctx) -> {
                    McpProgressReporter reporter = ctx.progressReporter();
                    reporter.report(Double.NaN, null, "bad");
                    return CompletableFuture.completedFuture(
                            new cn.richie696.component.mcp.api.model.McpToolResponse(
                                    List.of(), Map.of(), false));
                }));
        McpServerHttpEndpoint endpoint = newEndpoint(
                tools, new McpResourceRegistry(), new McpPromptRegistry());

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "nan-task");
        params.put("arguments", Map.of());
        params.put("progressToken", "tok-3");
        params.put("_meta", defaultMeta());
        McpHttpResponse response = endpoint.handle(
                body("tools/call", 11, params),
                toolCallHeaders("nan-task"));

        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("tools/list：传入 pageSize 时按 pageSize 取前 N 条")
    void toolsListRespectsPageSize() {
        McpToolRegistry tools = newToolRegistry();
        for (int i = 0; i < 5; i++) {
            String name = "tool-" + i;
            tools.register(new cn.richie696.component.mcp.server.tool.McpToolRegistration(
                    new cn.richie696.component.mcp.api.model.McpToolDescriptor(
                            name, null, null,
                            Map.of("type", "object"), Map.of(), Map.of()),
                    (args, ctx) -> CompletableFuture.completedFuture(
                            new cn.richie696.component.mcp.api.model.McpToolResponse(
                                    List.of(), Map.of(), false))));
        }
        McpServerHttpEndpoint endpoint = newEndpoint(
                tools, new McpResourceRegistry(), new McpPromptRegistry());

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("pageSize", 2);
        params.put("_meta", defaultMeta());
        McpHttpResponse response = endpoint.handle(
                body("tools/list", 99, params),
                validHeadersFor("tools/list"));

        assertThat(response.status()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) response.body();
        @SuppressWarnings("unchecked")
        Map<String, Object> page = (Map<String, Object>) envelope.get("result");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) page.get("tools");
        assertThat(list).hasSize(2);
    }

    @Test
    @DisplayName("tools/list：pageSize 非法（非 1..100 的整数）抛协议错误")
    void toolsListRejectsInvalidPageSize() {
        McpToolRegistry tools = newToolRegistry();
        McpServerHttpEndpoint endpoint = newEndpoint(
                tools, new McpResourceRegistry(), new McpPromptRegistry());

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("pageSize", 200);
        params.put("_meta", defaultMeta());
        McpHttpResponse response = endpoint.handle(
                body("tools/list", 99, params),
                validHeadersFor("tools/list"));

        assertThat(response.status()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) response.body();
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) envelope.get("error");
        assertThat(error.get("message").toString()).contains("pageSize");
    }

    @Test
    @DisplayName("subscriptions/listen：params.notifications 缺失抛协议错误")
    void subscriptionsListenRejectsMissingNotifications() {
        McpServerHttpEndpoint endpoint = newEndpoint(
                newToolRegistry(), new McpResourceRegistry(), new McpPromptRegistry());

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("_meta", defaultMeta());
        McpHttpResponse response = endpoint.handle(
                body("subscriptions/listen", 5, params),
                validHeadersFor("subscriptions/listen"));

        assertThat(response.status()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) response.body();
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) envelope.get("error");
        assertThat(error.get("message").toString()).contains("notifications");
    }

    @Test
    @DisplayName("tools/list：注册多个工具时返回的 tools 已按字典序排序")
    void toolsListReturnsSortedTools() {
        McpToolRegistry tools = newToolRegistry();
        for (String name : new String[]{"z-tool", "a-tool", "m-tool"}) {
            tools.register(new cn.richie696.component.mcp.server.tool.McpToolRegistration(
                    new cn.richie696.component.mcp.api.model.McpToolDescriptor(
                            name, null, null,
                            Map.of("type", "object"), Map.of(), Map.of()),
                    (args, ctx) -> CompletableFuture.completedFuture(
                            new cn.richie696.component.mcp.api.model.McpToolResponse(
                                    List.of(), Map.of(), false))));
        }
        McpServerHttpEndpoint endpoint = newEndpoint(
                tools, new McpResourceRegistry(), new McpPromptRegistry());

        McpHttpResponse response = endpoint.handle(
                body("tools/list", 99, Map.of()),
                validHeadersFor("tools/list"));

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) response.body();
        @SuppressWarnings("unchecked")
        Map<String, Object> page = (Map<String, Object>) envelope.get("result");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) page.get("tools");
        assertThat(list).extracting(item -> item.get("name"))
                .containsExactly("a-tool", "m-tool", "z-tool");
    }

    // ---- helpers ----

    private static McpToolRegistry newToolRegistry() {
        return new McpToolRegistry(McpToolVisibilityPolicy.ALLOW_ALL);
    }

    private static McpServerHttpEndpoint newEndpoint(
            McpToolRegistry tools,
            McpResourceRegistry resources,
            McpPromptRegistry prompts) {
        return new McpServerHttpEndpoint(
                tools,
                new McpImplementationInfo("atlas-richie-test", "1.0"),
                origin -> true,
                resources,
                prompts);
    }

    private static McpServerHttpEndpoint newEndpointWithCompletionRegistry(
            McpToolRegistry tools,
            McpCompletionRegistry completionRegistry) {
        return new McpServerHttpEndpoint(
                tools,
                new McpImplementationInfo("atlas-richie-test", "1.0"),
                origin -> true,
                new McpResourceRegistry(),
                new McpPromptRegistry(),
                completionRegistry);
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

    private static Map<String, List<String>> resourceReadHeaders(String uri) {
        Map<String, List<String>> headers = validHeadersFor("resources/read");
        headers.put("Mcp-Name", List.of(uri));
        return headers;
    }

    private static Map<String, List<String>> promptsGetHeaders(String name) {
        Map<String, List<String>> headers = validHeadersFor("prompts/get");
        headers.put("Mcp-Name", List.of(name));
        return headers;
    }

    private static String body(String method, Object id, Map<String, Object> params) {
        return "{\"jsonrpc\":\"2.0\"," +
                (id != null ? "\"id\":" + (id instanceof String ? "\"" + id + "\"" : id) + "," : "") +
                "\"method\":\"" + method + "\"," +
                "\"params\":" + tools.jackson.databind.json.JsonMapper.builder().build()
                        .writeValueAsString(paramsWithMeta(params)) + "}";
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
}
