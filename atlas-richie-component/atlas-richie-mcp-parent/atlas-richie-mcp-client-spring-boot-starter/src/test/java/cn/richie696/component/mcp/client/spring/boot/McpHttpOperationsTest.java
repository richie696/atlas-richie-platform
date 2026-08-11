/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.mcp.client.spring.boot;

import cn.richie696.component.mcp.api.McpClientRequest;
import cn.richie696.component.mcp.api.model.McpCompletionResult;
import cn.richie696.component.mcp.api.model.McpPromptContent;
import cn.richie696.component.mcp.api.model.McpPromptDescriptor;
import cn.richie696.component.mcp.api.model.McpResourceContent;
import cn.richie696.component.mcp.api.model.McpResourceDescriptor;
import cn.richie696.component.mcp.api.model.McpResourceTemplateDescriptor;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.protocol.compatibility.McpProtocolEraCache;
import cn.richie696.component.mcp.protocol.discovery.McpCacheScope;
import cn.richie696.component.mcp.protocol.discovery.McpDiscoverResult;
import cn.richie696.component.mcp.protocol.model.McpImplementationInfo;
import cn.richie696.component.mcp.security.oauth.McpOAuthAccessToken;
import cn.richie696.component.mcp.security.oauth.McpOAuthTokenProvider;
import cn.richie696.component.mcp.transport.http.McpHttpToolClient;
import cn.richie696.component.mcp.transport.http.McpRemoteTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 验证 {@link McpHttpOperations} 在按 serverId 与按 {@link McpClientRequest} 两种路由下的行为契约：
 * 未知 server 抛错；结果缓存命中/失效；OAuth Token Provider 存在时禁用结果缓存；动态 endpoint
 * 透传 request headers；缓存清理 SPI 同步结果缓存与协议 Era 缓存。
 *
 * @author richie696
 * @since 2026-08-11
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("McpHttpOperations 门面行为契约")
class McpHttpOperationsTest {

    @Mock
    private McpHttpToolClient client;

    private McpClientProperties properties;
    private McpProtocolEraCache eraCache;
    private McpClientResultCache resultCache;

    @BeforeEach
    void setUp() {
        properties = new McpClientProperties();
        properties.setNegotiateProtocol(false);
        McpClientProperties.Server server = new McpClientProperties.Server();
        server.setEndpoint("https://mcp.example.com/v1");
        properties.setServers(Map.of("demo", server));

        eraCache = new McpProtocolEraCache();
        resultCache = new McpClientResultCache();
    }

    private McpHttpOperations newOps() {
        return new McpHttpOperations(client, properties, eraCache, resultCache);
    }

    /**
     * Mockito 默认对非基本类型返回 null，{@link McpHttpToolClient#forProtocolVersion}
     * 是 final 类的方法也遵循这一规则，所以必须显式桥接到同一 mock。
     */
    private void stubForProtocolVersionBridge() {
        given(client.forProtocolVersion(anyString())).willAnswer(invocation -> client);
    }

    @Test
    @DisplayName("构造器对 null client/properties/eraCache/resultCache 抛 NPE")
    void constructorRejectsNullDependencies() {
        assertThatThrownBy(() -> new McpHttpOperations(null, properties, eraCache, resultCache))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("client");
        assertThatThrownBy(() -> new McpHttpOperations(client, null, eraCache, resultCache))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("properties");
        assertThatThrownBy(() -> new McpHttpOperations(client, properties, null, resultCache))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("protocolEraCache");
        assertThatThrownBy(() -> new McpHttpOperations(client, properties, eraCache, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("resultCache");
    }

    @Test
    @DisplayName("listTools(serverId) 命中服务端后会复用结果缓存")
    void listToolsUsesResultCacheAcrossInvocations() {
        McpHttpOperations operations = newOps();
        given(client.listTools(any(URI.class), anyMap()))
                .willReturn(List.of(remoteTool("echo")));

        List<McpToolDescriptor> first = operations.listTools("demo")
                .toCompletableFuture().join();
        List<McpToolDescriptor> second = operations.listTools("demo")
                .toCompletableFuture().join();

        assertThat(first).hasSize(1).extracting(McpToolDescriptor::name).containsExactly("echo");
        assertThat(second).isEqualTo(first);
        verify(client, times(1)).listTools(any(URI.class), anyMap());
    }

    @Test
    @DisplayName("write 类方法（callTool/readResource/getPrompt/complete）不写缓存")
    void writeOperationsSkipResultCache() {
        McpHttpOperations operations = newOps();
        given(client.callTool(any(URI.class), anyString(), anyMap(), anyMap()))
                .willReturn(new McpToolResponse(List.of(), Map.of(), false));
        given(client.readResource(any(URI.class), anyString(), anyMap()))
                .willReturn(new McpResourceContent(List.of()));
        given(client.getPrompt(any(URI.class), anyString(), anyMap(), anyMap()))
                .willReturn(new McpPromptContent("desc", List.of()));
        given(client.complete(any(URI.class), anyMap(), anyString(), anyString(), anyMap(), anyMap()))
                .willReturn(new McpCompletionResult(List.of("a"), 1, false));

        McpToolResponse tool = operations.callTool("demo", "echo", Map.of("x", 1))
                .toCompletableFuture().join();
        McpResourceContent resource = operations.readResource("demo", "file://x")
                .toCompletableFuture().join();
        McpPromptContent prompt = operations.getPrompt("demo", "p", Map.of())
                .toCompletableFuture().join();
        McpCompletionResult completion = operations.complete(
                "demo", Map.of("type", "ref"), "name", "v", Map.of())
                .toCompletableFuture().join();

        assertThat(tool).isNotNull();
        assertThat(resource).isNotNull();
        assertThat(prompt).isNotNull();
        assertThat(completion).isNotNull();
        assertThat(resultCache.get("demo|callTool")).isEmpty();
        assertThat(resultCache.get("demo|readResource")).isEmpty();
    }

    @Test
    @DisplayName("未知 serverId 在所有静态方法上抛 IllegalArgumentException")
    void unknownServerIdRaisesIllegalArgument() {
        McpHttpOperations operations = newOps();

        assertThatThrownBy(() -> operations.listTools("missing").toCompletableFuture().join())
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown MCP server");
        assertThatThrownBy(() -> operations.callTool("missing", "echo", Map.of())
                .toCompletableFuture().join())
                .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> operations.listResources("missing").toCompletableFuture().join())
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("空白 serverId 抛 IllegalArgumentException（fail-fast 校验）")
    void blankServerIdRaisesIllegalArgument() {
        McpHttpOperations operations = newOps();
        assertThatThrownBy(() -> operations.listTools("  ").toCompletableFuture().join())
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    @DisplayName("OAuth Token Provider 存在时，结果缓存被禁用但请求被发送")
    void oauthProviderDisablesResultCache() {
        McpOAuthTokenProvider tokenProvider = (resource, scopes) ->
                CompletableFuture.completedFuture(Optional.of(
                        new McpOAuthAccessToken("token", "Bearer",
                                Instant.now().plusSeconds(60), null, null, Set.of())));
        given(client.listTools(any(URI.class), anyMap()))
                .willReturn(List.of(remoteTool("echo")));

        McpHttpOperations operations = new McpHttpOperations(
                client, properties, eraCache, resultCache, tokenProvider);

        List<McpToolDescriptor> first = operations.listTools("demo").toCompletableFuture().join();
        List<McpToolDescriptor> second = operations.listTools("demo").toCompletableFuture().join();

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        verify(client, times(2)).listTools(any(URI.class), anyMap());
    }

    @Test
    @DisplayName("listTools 缓存关闭时，每次都透传到远端")
    void resultCacheDisabledForcesRemoteEveryTime() {
        properties.setResultCacheEnabled(false);
        McpHttpOperations operations = newOps();
        given(client.listTools(any(URI.class), anyMap()))
                .willReturn(List.of(remoteTool("echo")));

        operations.listTools("demo").toCompletableFuture().join();
        operations.listTools("demo").toCompletableFuture().join();

        verify(client, times(2)).listTools(any(URI.class), anyMap());
        assertThat(resultCache.get("demo|tools/list")).isEmpty();
    }

    @Test
    @DisplayName("dynamic listTools 透传 request headers 并禁用结果缓存")
    void dynamicListToolsForwardsRequestHeaders() {
        McpHttpOperations operations = newOps();
        given(client.listTools(any(URI.class), anyMap()))
                .willReturn(List.of(remoteTool("dynamic")));

        McpClientRequest request = new McpClientRequest(
                "discovered", URI.create("https://other.example.com/mcp"),
                Map.of("Authorization", "Bearer abc"));
        List<McpToolDescriptor> tools = operations.listTools(request)
                .toCompletableFuture().join();

        assertThat(tools).extracting(McpToolDescriptor::name).containsExactly("dynamic");
        verify(client).listTools(eq(URI.create("https://other.example.com/mcp")), anyMap());
        assertThat(resultCache.get("discovered|tools/list")).isEmpty();
    }

    @Test
    @DisplayName("dynamic callTool 透传 arguments 与 request headers")
    void dynamicCallToolForwardsArgumentsAndHeaders() {
        McpHttpOperations operations = newOps();
        McpToolResponse response = new McpToolResponse(
                List.of(Map.of("type", "text", "text", "ok")),
                Map.of("ok", true), false);
        given(client.callTool(any(URI.class), anyString(), anyMap(), anyMap()))
                .willReturn(response);

        McpClientRequest request = new McpClientRequest(
                "r", URI.create("https://api.example.com/mcp"), Map.of());
        McpToolResponse actual = operations.callTool(request, "echo", Map.of("k", "v"))
                .toCompletableFuture().join();

        assertThat(actual).isSameAs(response);
        verify(client).callTool(eq(URI.create("https://api.example.com/mcp")),
                eq("echo"), eq(Map.of("k", "v")), anyMap());
    }

    @Test
    @DisplayName("静态 listResources / listResourceTemplates / listPrompts 写入结果缓存")
    void readOperationsUseResultCache() {
        McpHttpOperations operations = newOps();
        given(client.listResources(any(URI.class), anyMap()))
                .willReturn(List.of());
        given(client.listResourceTemplates(any(URI.class), anyMap()))
                .willReturn(List.of());
        given(client.listPrompts(any(URI.class), anyMap()))
                .willReturn(List.of());

        operations.listResources("demo").toCompletableFuture().join();
        operations.listResourceTemplates("demo").toCompletableFuture().join();
        operations.listPrompts("demo").toCompletableFuture().join();
        operations.listResources("demo").toCompletableFuture().join();
        operations.listResourceTemplates("demo").toCompletableFuture().join();
        operations.listPrompts("demo").toCompletableFuture().join();

        assertThat(resultCache.get("demo|resources/list")).isPresent();
        assertThat(resultCache.get("demo|resources/templates/list")).isPresent();
        assertThat(resultCache.get("demo|prompts/list")).isPresent();
        verify(client, times(1)).listResources(any(URI.class), anyMap());
        verify(client, times(1)).listResourceTemplates(any(URI.class), anyMap());
        verify(client, times(1)).listPrompts(any(URI.class), anyMap());
    }

    @Test
    @DisplayName("dynamic listResources / listResourceTemplates / readResource / listPrompts / getPrompt / complete 透传到远端")
    void dynamicReadOperationsForwardToRemote() {
        McpHttpOperations operations = newOps();
        given(client.listResources(any(URI.class), anyMap())).willReturn(List.of());
        given(client.listResourceTemplates(any(URI.class), anyMap())).willReturn(List.of());
        given(client.readResource(any(URI.class), anyString(), anyMap()))
                .willReturn(new McpResourceContent(List.of()));
        given(client.listPrompts(any(URI.class), anyMap())).willReturn(List.of());
        given(client.getPrompt(any(URI.class), anyString(), anyMap(), anyMap()))
                .willReturn(new McpPromptContent("d", List.of()));
        given(client.complete(any(URI.class), anyMap(), anyString(), anyString(), anyMap(), anyMap()))
                .willReturn(new McpCompletionResult(List.of(), 0, false));

        McpClientRequest request = new McpClientRequest(
                "r", URI.create("https://api.example.com/mcp"), Map.of());
        operations.listResources(request).toCompletableFuture().join();
        operations.listResourceTemplates(request).toCompletableFuture().join();
        operations.readResource(request, "file://x").toCompletableFuture().join();
        operations.listPrompts(request).toCompletableFuture().join();
        operations.getPrompt(request, "p", Map.of()).toCompletableFuture().join();
        operations.complete(request, Map.of(), "name", "v", Map.of()).toCompletableFuture().join();

        verify(client).listResources(eq(URI.create("https://api.example.com/mcp")), anyMap());
        verify(client).listResourceTemplates(eq(URI.create("https://api.example.com/mcp")), anyMap());
        verify(client).readResource(eq(URI.create("https://api.example.com/mcp")), eq("file://x"), anyMap());
        verify(client).listPrompts(eq(URI.create("https://api.example.com/mcp")), anyMap());
        verify(client).getPrompt(eq(URI.create("https://api.example.com/mcp")), eq("p"), anyMap(), anyMap());
        verify(client).complete(eq(URI.create("https://api.example.com/mcp")), anyMap(),
                eq("name"), eq("v"), anyMap(), anyMap());
    }

    @Test
    @DisplayName("dynamic 方法在 negotiateProtocol=true 时也会走协商路径")
    void dynamicEndpointAlsoNegotiatesProtocol() {
        properties.setNegotiateProtocol(true);
        McpHttpOperations operations = newOps();
        stubForProtocolVersionBridge();

        McpDiscoverResult discovery = new McpDiscoverResult(
                List.of("2026-07-28"),
                Map.of(),
                new McpImplementationInfo("s", "1"),
                null, 30_000L, McpCacheScope.PUBLIC, Map.of());
        given(client.discover(any(URI.class), anyMap())).willReturn(discovery);
        given(client.listTools(any(URI.class), anyMap()))
                .willReturn(List.of(remoteTool("dyn")));

        McpClientRequest request = new McpClientRequest(
                "r", URI.create("https://other.example.com/mcp"), Map.of());
        operations.listTools(request).toCompletableFuture().join();

        assertThat(eraCache.get("https://other.example.com/mcp")).isPresent();
    }

    @Test
    @DisplayName("动态 endpoint 命中 Era 缓存时直接复用，跳过 discover")
    void dynamicEndpointReusesEraCache() {
        properties.setNegotiateProtocol(true);
        McpHttpOperations operations = newOps();
        stubForProtocolVersionBridge();
        eraCache.put("https://other.example.com/mcp", "2026-07-28", Duration.ofMinutes(5));
        given(client.listTools(any(URI.class), anyMap()))
                .willReturn(List.of(remoteTool("dyn")));

        McpClientRequest request = new McpClientRequest(
                "r", URI.create("https://other.example.com/mcp"), Map.of());
        operations.listTools(request).toCompletableFuture().join();

        verify(client, never()).discover(any(URI.class), anyMap());
        verify(client, times(1)).listTools(any(URI.class), anyMap());
    }

    @Test
    @DisplayName("endpoint 非法 URL 在 listTools(serverId) 上抛 IllegalArgumentException")
    void invalidServerEndpointRaisesIllegalArgument() {
        McpClientProperties.Server bad = new McpClientProperties.Server();
        bad.setEndpoint("not a valid uri %%%%");
        properties.setServers(Map.of("bad", bad));
        McpHttpOperations operations = newOps();

        assertThatThrownBy(() -> operations.listTools("bad").toCompletableFuture().join())
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid MCP endpoint");
    }

    @Test
    @DisplayName("OAuth 启用时，资源 URI 为 null 时回退到 endpoint")
    void oauthResourceFallsBackToEndpoint() {
        McpOAuthTokenProvider tokenProvider = (resource, scopes) ->
                CompletableFuture.completedFuture(Optional.of(
                        new McpOAuthAccessToken("tok", "Bearer",
                                Instant.now().plusSeconds(60), null, null, Set.of())));
        given(client.listTools(any(URI.class), anyMap()))
                .willReturn(List.of(remoteTool("echo")));

        McpHttpOperations operations = new McpHttpOperations(
                client, properties, eraCache, resultCache, tokenProvider);

        operations.listTools("demo").toCompletableFuture().join();

        verify(client).listTools(eq(URI.create("https://mcp.example.com/v1")), anyMap());
    }

    @Test
    @DisplayName("OAuth 启用且 resource 为空白时，token 仍按 endpoint 解析")
    void oauthResourceBlankFallsBackToEndpoint() {
        McpClientProperties.Server server = properties.getServers().get("demo");
        server.setResource("  ");
        McpOAuthTokenProvider tokenProvider = (resource, scopes) ->
                CompletableFuture.completedFuture(Optional.of(
                        new McpOAuthAccessToken("tok", "Bearer",
                                Instant.now().plusSeconds(60), null, null, Set.of())));
        given(client.listTools(any(URI.class), anyMap()))
                .willReturn(List.of(remoteTool("echo")));

        McpHttpOperations operations = new McpHttpOperations(
                client, properties, eraCache, resultCache, tokenProvider);

        operations.listTools("demo").toCompletableFuture().join();

        verify(client).listTools(eq(URI.create("https://mcp.example.com/v1")), anyMap());
    }

    @Test
    @DisplayName("dynamic 方法在 request 为 null 时抛 NPE")
    void dynamicMethodsRejectNullRequest() {
        McpHttpOperations operations = newOps();
        McpClientRequest nullRequest = null;
        assertThatThrownBy(() -> operations.listTools(nullRequest))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("request");
        assertThatThrownBy(() -> operations.callTool(nullRequest, "x", Map.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("invalidateServerCache 同时清掉结果缓存与协议 Era 缓存")
    void invalidateServerCacheClearsBothStores() {
        McpHttpOperations operations = newOps();
        resultCache.put("demo|tools/list", "v", Duration.ofMinutes(1));
        eraCache.put("https://mcp.example.com/v1", "2026-07-28", Duration.ofMinutes(1));

        operations.invalidateServerCache("demo");

        assertThat(resultCache.get("demo|tools/list")).isEmpty();
        assertThat(eraCache.get("https://mcp.example.com/v1")).isEmpty();
    }

    @Test
    @DisplayName("clearCaches 清空所有缓存")
    void clearCachesClearsEverything() {
        McpHttpOperations operations = newOps();
        resultCache.put("demo|tools/list", "v", Duration.ofMinutes(1));
        eraCache.put("https://mcp.example.com/v1", "2026-07-28", Duration.ofMinutes(1));

        operations.clearCaches();

        assertThat(resultCache.get("demo|tools/list")).isEmpty();
        assertThat(eraCache.get("https://mcp.example.com/v1")).isEmpty();
    }

    @Test
    @DisplayName("negotiateProtocol=true 且缓存为空时执行协议协商并写入 Era 缓存")
    void protocolNegotiationPopulatesEraCache() {
        properties.setNegotiateProtocol(true);
        McpHttpOperations operations = newOps();
        stubForProtocolVersionBridge();

        McpDiscoverResult discovery = new McpDiscoverResult(
                List.of("2026-07-28"),
                Map.of(),
                new McpImplementationInfo("server", "1.0"),
                null,
                30_000L,
                McpCacheScope.PUBLIC,
                Map.of());
        given(client.discover(any(URI.class), anyMap())).willReturn(discovery);
        given(client.listTools(any(URI.class), anyMap()))
                .willReturn(List.of(remoteTool("echo")));

        operations.listTools("demo").toCompletableFuture().join();

        assertThat(eraCache.get("https://mcp.example.com/v1")).isPresent();
        verify(client, times(1)).discover(any(URI.class), anyMap());
        verify(client, times(1)).listTools(any(URI.class), anyMap());
    }

    @Test
    @DisplayName("协议协商走最小 TTL：远端 ttlMs 小于 configuredTtl 时使用远端值")
    void negotiationUsesSmallerTtl() {
        properties.setNegotiateProtocol(true);
        properties.setNegotiationTtl(Duration.ofMinutes(10));
        McpHttpOperations operations = newOps();
        stubForProtocolVersionBridge();

        McpDiscoverResult discovery = new McpDiscoverResult(
                List.of("2026-07-28"),
                Map.of(),
                new McpImplementationInfo("s", "1"),
                null,
                1_000L, // 1 秒
                McpCacheScope.PUBLIC,
                Map.of());
        given(client.discover(any(URI.class), anyMap())).willReturn(discovery);
        given(client.listTools(any(URI.class), anyMap()))
                .willReturn(List.of(remoteTool("echo")));

        operations.listTools("demo").toCompletableFuture().join();
        operations.listTools("demo").toCompletableFuture().join();

        verify(client, times(1)).discover(any(URI.class), anyMap());
    }

    @Test
    @DisplayName("协议协商未命中任何共同版本时抛协议错误（通过 mock 让对端支持集合为空模拟）")
    void negotiationWithoutMatchThrows() {
        properties.setNegotiateProtocol(true);
        McpHttpOperations operations = newOps();
        stubForProtocolVersionBridge();

        McpDiscoverResult discovery = new McpDiscoverResult(
                List.of("unknown-version"),
                Map.of(),
                null, null, 0L, McpCacheScope.PUBLIC, Map.of());
        given(client.discover(any(URI.class), anyMap())).willReturn(discovery);

        CompletionStage<List<McpToolDescriptor>> stage = operations.listTools("demo");
        assertThatThrownBy(stage.toCompletableFuture()::join)
                .hasCauseInstanceOf(cn.richie696.component.mcp.protocol.McpProtocolException.class);
        verify(client, never()).listTools(any(URI.class), anyMap());
    }

    private static McpRemoteTool remoteTool(String name) {
        return new McpRemoteTool(name, name, "desc",
                Map.of("type", "object"),
                Map.of("type", "object"),
                Map.of());
    }
}
