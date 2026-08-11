package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.model.McpImplementationInfo;
import cn.richie696.component.mcp.server.prompt.McpPromptRegistry;
import cn.richie696.component.mcp.server.resource.McpResourceRegistry;
import cn.richie696.component.mcp.server.tool.McpRequiredScopeVisibilityPolicy;
import cn.richie696.component.mcp.server.tool.McpToolRegistration;
import cn.richie696.component.mcp.server.tool.McpToolRegistry;
import cn.richie696.component.mcp.server.tool.McpToolVisibilityPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpServerHttpEndpoint} 与 registry 变更事件的耦合：工具注册 / 替换会
 * 经 {@link McpSubscriptionManager} 推送 {@code notifications/tools/list_changed}；
 * 自定义 context factory 能从 header 抽取 tenant / principal / scopes 并产出
 * 满足权限策略的 {@link McpCallContext}。该测试覆盖 HTTP 端点对 registry 事件
 * 与多租户上下文的协同边界。
 *
 * @author richie696
 * @since 2026-08-11
 */
class McpServerHttpEndpointRegistryNotificationTest {
    @Test
    void registryRevisionPublishesToolsListChangedNotification() throws Exception {
        McpToolRegistry registry = new McpToolRegistry();
        McpServerHttpEndpoint endpoint = new McpServerHttpEndpoint(
                registry, new McpImplementationInfo("test", "1.0"));
        McpSubscriptionManager.Subscription subscription = endpoint.subscriptionManager().open(
                "subscription-1", new McpSubscriptionSpec(true, false, false, Set.of()));
        CountDownLatch received = new CountDownLatch(1);
        List<Map<String, Object>> notifications = new CopyOnWriteArrayList<>();
        subscription.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription flowSubscription) {
                flowSubscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Map<String, Object> item) {
                notifications.add(item);
                received.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });

        registry.register(new McpToolRegistration(
                new McpToolDescriptor(
                        "inventory.query", null, null,
                        Map.of("type", "object"), Map.of(), Map.of()),
                (arguments, context) -> CompletableFuture.completedFuture(
                        new McpToolResponse(List.of(), Map.of(), false))));

        assertThat(received.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(notifications).singleElement().satisfies(notification ->
                assertThat(notification).containsEntry(
                        "method", "notifications/tools/list_changed"));
    }

    @Test
    void customContextFactorySuppliesTenantPrincipalAndScopes() {
        McpToolRegistry registry = new McpToolRegistry(
                new McpRequiredScopeVisibilityPolicy(McpToolVisibilityPolicy.ALLOW_ALL));
        registry.register(new McpToolRegistration(
                new McpToolDescriptor(
                        "orders.read", null, null,
                        Map.of("type", "object"), Map.of(),
                        Map.of("requiredScopes", List.of("orders:read"))),
                (arguments, context) -> CompletableFuture.completedFuture(
                        new McpToolResponse(List.of(), Map.of(), false))));
        McpServerHttpEndpoint endpoint = new McpServerHttpEndpoint(
                registry,
                new McpImplementationInfo("test", "1.0"),
                origin -> true,
                new McpResourceRegistry(),
                new McpPromptRegistry(),
                null,
                List.of(),
                request -> new McpCallContext(
                        request.requestId(), request.protocolVersion(),
                        request.headers().get("X-Tenant").getFirst(), "alice",
                        request.defaultDeadline(), Map.of("scopes", Set.of("orders:read")),
                        request.cancellationToken(), request.progressReporter()));
        String body = """
                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{"_meta":{
                  "io.modelcontextprotocol/protocolVersion":"2026-07-28",
                  "io.modelcontextprotocol/clientCapabilities":{}}}}
                """;
        Map<String, List<String>> headers = Map.of(
                "Content-Type", List.of("application/json"),
                "Accept", List.of("application/json, text/event-stream"),
                "MCP-Protocol-Version", List.of(McpProtocolVersions.V_2026_07_28),
                "Mcp-Method", List.of("tools/list"),
                "X-Tenant", List.of("tenant-1"));

        McpHttpResponse response = endpoint.handle(body, headers);

        assertThat(response.status()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) response.body();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) envelope.get("result");
        assertThat((List<?>) result.get("tools")).hasSize(1);
    }
}
