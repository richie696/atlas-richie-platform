package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.api.model.McpCompletionResult;
import cn.richie696.component.mcp.api.model.McpPromptContent;
import cn.richie696.component.mcp.api.model.McpPromptDescriptor;
import cn.richie696.component.mcp.api.model.McpResourceContent;
import cn.richie696.component.mcp.api.model.McpResourceDescriptor;
import cn.richie696.component.mcp.api.model.McpResourceTemplateDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.discovery.McpCacheScope;
import cn.richie696.component.mcp.protocol.discovery.McpDiscoverResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 通过 mock {@link HttpClient} 验证 {@link McpHttpToolClient} 的契约：
 * 构造器对协议版本与分页上限的校验；九种业务方法（{@code discover} / {@code listTools} /
 * {@code listResources} / {@code listResourceTemplates} / {@code listPrompts} /
 * {@code readResource} / {@code getPrompt} / {@code callTool} / {@code complete}）
 * 在正常与异常输入下的解析路径；SSE 响应通过 {@code data:} 行提取最后一帧；非 2xx
 * 状态 + {@code error} envelope 映射为携带 {@link cn.richie696.component.mcp.protocol.McpProtocolException}
 * 的 {@link McpHttpClientException}；URL 路径为根时规范化追加 {@code /mcp}。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpHttpToolClient HTTP 客户端")
class McpHttpToolClientTest {

    private static final URI ENDPOINT = URI.create("https://mcp.example.com/");

    @Test
    @DisplayName("构造器：httpClient / requestTimeout / 客户端名不允许 null 或空白")
    void constructorEnforcesNonNullArgs() {
        assertThatThrownBy(() -> new McpHttpToolClient(
                null, Duration.ofSeconds(10)))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new McpHttpToolClient(
                HttpClient.newHttpClient(), null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new McpHttpToolClient(
                HttpClient.newHttpClient(),
                Duration.ofSeconds(10),
                "  ", "1.0"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new McpHttpToolClient(
                HttpClient.newHttpClient(),
                Duration.ofSeconds(10),
                "client", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("构造器：protocolVersion 不在 SUPPORTED 集合中时拒绝")
    void constructorRejectsUnsupportedProtocolVersion() {
        assertThatThrownBy(() -> new McpHttpToolClient(
                HttpClient.newHttpClient(),
                Duration.ofSeconds(10),
                "client",
                "1.0",
                "9999-01-01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("9999-01-01");
    }

    @Test
    @DisplayName("构造器：maxPages / maxItems 必须为正数")
    void constructorRejectsNonPositiveLimits() {
        assertThatThrownBy(() -> new McpHttpToolClient(
                HttpClient.newHttpClient(),
                Duration.ofSeconds(10),
                "client",
                "1.0",
                McpProtocolVersions.V_2026_07_28,
                0, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new McpHttpToolClient(
                HttpClient.newHttpClient(),
                Duration.ofSeconds(10),
                "client",
                "1.0",
                McpProtocolVersions.V_2026_07_28,
                1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("protocolVersion 返回构造时的版本号")
    void protocolVersionAccessor() {
        McpHttpToolClient client = new McpHttpToolClient(
                HttpClient.newHttpClient(), Duration.ofSeconds(10));
        assertThat(client.protocolVersion()).isEqualTo(McpProtocolVersions.V_2026_07_28);
    }

    @Test
    @DisplayName("forProtocolVersion 复制实例：共享 httpClient/超时但切换版本")
    void forProtocolVersionReturnsCopyWithNewVersion() {
        McpHttpToolClient original = new McpHttpToolClient(
                HttpClient.newHttpClient(), Duration.ofSeconds(10));
        McpHttpToolClient copy = original.forProtocolVersion(McpProtocolVersions.V_2025_11_25);

        assertThat(copy).isNotSameAs(original);
        assertThat(copy.protocolVersion()).isEqualTo(McpProtocolVersions.V_2025_11_25);
        assertThat(original.protocolVersion()).isEqualTo(McpProtocolVersions.V_2026_07_28);
    }

    @Test
    @DisplayName("discover：解码 result 字段为 McpDiscoverResult")
    void discoverDecodesServerDiscoverResponse() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{
                  "resultType":"complete",
                  "supportedVersions":["2026-07-28"],
                  "capabilities":{"tools":{}},
                  "ttlMs":300000,
                  "cacheScope":"public"}}
                """);

        McpDiscoverResult result = client.discover(ENDPOINT, Map.of());

        assertThat(result.supportedVersions()).containsExactly("2026-07-28");
        assertThat(result.ttlMs()).isEqualTo(300000L);
        assertThat(result.cacheScope()).isEqualTo(McpCacheScope.PUBLIC);
    }

    @Test
    @DisplayName("listTools：嵌套分页（cursor 终止 + 多页拼接）")
    void listToolsAggregatesAcrossPages() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(
                httpClient, Duration.ofSeconds(5), "client", "1.0",
                McpProtocolVersions.V_2026_07_28, 10, 100);
        HttpResponse<String> firstPage = stringResponse(200,
                "{\"jsonrpc\":\"2.0\",\"id\":\"x\",\"result\":{\"resultType\":\"complete\","
                        + "\"tools\":[{\"name\":\"a\",\"inputSchema\":{\"type\":\"object\"},"
                        + "\"outputSchema\":{},\"annotations\":{}}],"
                        + "\"nextCursor\":\"NEXT\"}}");
        HttpResponse<String> secondPage = stringResponse(200,
                "{\"jsonrpc\":\"2.0\",\"id\":\"y\",\"result\":{\"resultType\":\"complete\","
                        + "\"tools\":[{\"name\":\"b\",\"inputSchema\":{\"type\":\"object\"}}]}}");
        org.mockito.Mockito.doReturn(firstPage)
                .doReturn(secondPage)
                .when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        List<McpRemoteTool> tools = client.listTools(ENDPOINT, Map.of());

        assertThat(tools).extracting(McpRemoteTool::name).containsExactly("a", "b");
    }

    @Test
    @DisplayName("listTools：tools 字段非数组抛客户端错误")
    void listToolsRejectsNonArrayTools() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{"tools":"not-array"}}""");

        assertThatThrownBy(() -> client.listTools(ENDPOINT, Map.of()))
                .isInstanceOf(McpHttpClientException.class)
                .hasMessageContaining("tools/list result.tools must be an array");
    }

    @Test
    @DisplayName("listTools：单条 tool 不是对象抛客户端错误")
    void listToolsRejectsNonObjectEntries() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{"tools":["string-not-object"]}}""");

        assertThatThrownBy(() -> client.listTools(ENDPOINT, Map.of()))
                .isInstanceOf(McpHttpClientException.class)
                .hasMessageContaining("non-object tool");
    }

    @Test
    @DisplayName("listTools：tools[].name 缺失或空白抛客户端错误")
    void listToolsRequiresNonBlankName() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{"tools":[{"inputSchema":{}}]}}""");

        assertThatThrownBy(() -> client.listTools(ENDPOINT, Map.of()))
                .isInstanceOf(McpHttpClientException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("listTools：超过 maxPages 安全抛错")
    void listToolsEnforcesMaxPages() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(
                httpClient, Duration.ofSeconds(5), "client", "1.0",
                McpProtocolVersions.V_2026_07_28, 1, 100);
        HttpResponse<String> firstPage = stringResponse(200,
                "{\"jsonrpc\":\"2.0\",\"id\":\"x\",\"result\":{\"tools\":[],\"nextCursor\":\"NEXT\"}}");
        HttpResponse<String> secondPage = stringResponse(200,
                "{\"jsonrpc\":\"2.0\",\"id\":\"y\",\"result\":{\"tools\":[]}}");
        org.mockito.Mockito.doReturn(firstPage)
                .doReturn(secondPage)
                .when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        assertThatThrownBy(() -> client.listTools(ENDPOINT, Map.of()))
                .isInstanceOf(McpHttpClientException.class)
                .hasMessageContaining("maxPages");
    }

    @Test
    @DisplayName("listResources：分页 + 解码 Uri/Name/Size 等字段")
    void listResourcesDecodesPagedResults() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{"resultType":"complete",
                  "resources":[{
                    "uri":"file://a","name":"a","title":"A","description":"a-doc",
                    "mimeType":"text/plain","size":42,
                    "annotations":{"hint":"x"}}]}}""");

        List<McpResourceDescriptor> resources = client.listResources(ENDPOINT, Map.of());

        assertThat(resources).singleElement().satisfies(resource -> {
            assertThat(resource.uri()).isEqualTo("file://a");
            assertThat(resource.name()).isEqualTo("a");
            assertThat(resource.title()).isEqualTo("A");
            assertThat(resource.mimeType()).isEqualTo("text/plain");
            assertThat(resource.size()).isEqualTo(42L);
        });
    }

    @Test
    @DisplayName("listResourceTemplates：嵌套分页")
    void listResourceTemplatesDecodes() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{"resultType":"complete",
                  "resourceTemplates":[
                    {"uriTemplate":"file:///{id}","name":"tpl"}]}}""");

        List<McpResourceTemplateDescriptor> templates =
                client.listResourceTemplates(ENDPOINT, Map.of());

        assertThat(templates).singleElement()
                .extracting(McpResourceTemplateDescriptor::uriTemplate)
                .isEqualTo("file:///{id}");
    }

    @Test
    @DisplayName("listPrompts：解码 arguments 数组")
    void listPromptsDecodes() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{"resultType":"complete",
                  "prompts":[{"name":"greet","title":"G","description":"d",
                    "arguments":[{"name":"lang","required":true}]}]}}""");

        List<McpPromptDescriptor> prompts = client.listPrompts(ENDPOINT, Map.of());

        assertThat(prompts).singleElement().satisfies(prompt ->
                assertThat(prompt.arguments()).hasSize(1));
    }

    @Test
    @DisplayName("readResource：解码 contents 列表为 Map 列表")
    void readResourceDecodesContents() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{
                  "contents":[{"uri":"file://a","text":"hi"}]}}""");

        McpResourceContent content = client.readResource(ENDPOINT, "file://a", Map.of());

        assertThat(content.contents().get(0))
                .containsEntry("uri", "file://a")
                .containsEntry("text", "hi");
    }

    @Test
    @DisplayName("getPrompt：messages 数组解码")
    void getPromptDecodesMessages() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{
                  "description":"d",
                  "messages":[{"role":"user","content":{"text":"hi"}}]}}""");

        McpPromptContent prompt = client.getPrompt(
                ENDPOINT, "greet", Map.of("lang", "en"), Map.of());

        assertThat(prompt.description()).isEqualTo("d");
        assertThat(prompt.messages()).hasSize(1);
    }

    @Test
    @DisplayName("callTool：基本响应解码（content/isError/resultType）")
    void callToolDecodesBasicResponse() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{
                  "resultType":"complete",
                  "content":[{"type":"text","text":"hello"}],
                  "structuredContent":{"summary":"world"},
                  "isError":false}}""");

        McpToolResponse response = client.callTool(
                ENDPOINT, "lookup", Map.of("k", "v"), Map.of());

        assertThat(response.resultType()).isEqualTo("complete");
        assertThat(response.error()).isFalse();
        assertThat(response.content().get(0)).containsEntry("text", "hello");
        @SuppressWarnings("unchecked")
        Map<String, Object> structured = (Map<String, Object>) response.structuredContent();
        assertThat(structured).containsEntry("summary", "world");
    }

    @Test
    @DisplayName("callTool：MRTR input_required 时回传 inputRequests 与 requestState")
    void callToolDecodesInputRequiredResponse() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{
                  "resultType":"input_required",
                  "content":[],
                  "inputRequests":{"missing":"name"},
                  "requestState":"state-7"}}""");

        McpToolResponse response = client.callTool(
                ENDPOINT, "ask", Map.of(), Map.of());

        assertThat(response.resultType()).isEqualTo("input_required");
        assertThat(response.requestState()).isEqualTo("state-7");
        assertThat(response.inputRequests()).containsEntry("missing", "name");
    }

    @Test
    @DisplayName("callTool：省略 structuredContent 时不影响反序列化")
    void callToolDecodesWithoutStructuredContent() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{
                  "resultType":"complete",
                  "content":[{"type":"text","text":"plain"}]}}""");

        McpToolResponse response = client.callTool(
                ENDPOINT, "echo", Map.of(), Map.of());

        assertThat(response.structuredContent()).isNull();
        assertThat(response.content().get(0)).containsEntry("text", "plain");
    }

    @Test
    @DisplayName("getPrompt：messages 字段缺失时抛客户端错误")
    void getPromptRejectsMissingMessages() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{"description":"d"}}""");

        assertThatThrownBy(() -> client.getPrompt(
                ENDPOINT, "greet", Map.of("lang", "en"), Map.of()))
                .isInstanceOf(McpHttpClientException.class)
                .hasMessageContaining("result.messages must be an array");
    }

    @Test
    @DisplayName("complete：contextArguments 序列化进 envelope")
    void completeEncodesContextArguments() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{"resultType":"complete",
                  "completion":{"values":["a"]}}}""");

        client.complete(ENDPOINT, Map.of("type", "ref/prompt"),
                "n", "v",
                Map.of("Authorization", "Bearer x"),
                Map.of("ctx", "k1"));

        org.mockito.ArgumentCaptor<HttpRequest> captor =
                org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        String body = readRequestBody(captor.getValue());
        assertThat(body).contains("\"arguments\":{\"ctx\":\"k1\"}");
    }

    @Test
    @DisplayName("complete：total 字段存在时返回 int 值")
    void completeDecodesTotal() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{"resultType":"complete",
                  "completion":{"values":[],"total":42,"hasMore":true}}}""");

        McpCompletionResult completion = client.complete(
                ENDPOINT, Map.of("type", "ref/prompt"),
                "n", "v", Map.of(), null);

        assertThat(completion.total()).isEqualTo(42);
        assertThat(completion.hasMore()).isTrue();
    }

    @Test
    @DisplayName("listTools：cursor 不存在时返回 null（在 maxPages 范围内）")
    void listToolsCursorAbsentReturnsAll() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(
                httpClient, Duration.ofSeconds(5), "client", "1.0",
                McpProtocolVersions.V_2026_07_28, 10, 100);
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{
                  "resultType":"complete",
                  "tools":[{"name":"a","inputSchema":{}}]}}""");

        List<McpRemoteTool> tools = client.listTools(ENDPOINT, Map.of());

        assertThat(tools).singleElement().extracting(McpRemoteTool::name).isEqualTo("a");
    }

    @Test
    @DisplayName("readResource：返回 contents 列表（成功路径）")
    void readResourceReturnsContents() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{
                  "contents":[{"uri":"file://a","text":"hi"}]}}""");

        McpResourceContent content = client.readResource(ENDPOINT, "file://a", Map.of());

        assertThat(content.contents().get(0))
                .containsEntry("uri", "file://a")
                .containsEntry("text", "hi");
    }

    @Test
    @DisplayName("getPrompt：description 缺失时默认为空字符串")
    void getPromptDefaultDescriptionIsEmptyString() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{
                  "messages":[]}}""");

        McpPromptContent prompt = client.getPrompt(
                ENDPOINT, "greet", Map.of(), Map.of());

        assertThat(prompt.description()).isEqualTo("");
    }

    @Test
    @DisplayName("listResources：每条 resource 缺 uri 抛客户端错误")
    void listResourcesRejectsEntryWithoutUri() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{
                  "resources":[{"name":"no-uri"}]}}""");

        assertThatThrownBy(() -> client.listResources(ENDPOINT, Map.of()))
                .isInstanceOf(McpHttpClientException.class)
                .hasMessageContaining("resources[].uri");
    }

    @Test
    @DisplayName("listResources：每条 resource 缺 name 抛客户端错误")
    void listResourcesRejectsEntryWithoutName() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{
                  "resources":[{"uri":"file://x"}]}}""");

        assertThatThrownBy(() -> client.listResources(ENDPOINT, Map.of()))
                .isInstanceOf(McpHttpClientException.class)
                .hasMessageContaining("resources[].name");
    }

    @Test
    @DisplayName("listResourceTemplates：模板的 uriTemplate/name 必填校验")
    void listResourceTemplatesRejectsMissingUriTemplate() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{
                  "resourceTemplates":[{"name":"x"}]}}""");

        assertThatThrownBy(() -> client.listResourceTemplates(ENDPOINT, Map.of()))
                .isInstanceOf(McpHttpClientException.class)
                .hasMessageContaining("uriTemplate");
    }

    @Test
    @DisplayName("listPrompts：name 缺失抛客户端错误")
    void listPromptsRejectsEntryWithoutName() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{
                  "prompts":[{"description":"d"}]}}""");

        assertThatThrownBy(() -> client.listPrompts(ENDPOINT, Map.of()))
                .isInstanceOf(McpHttpClientException.class)
                .hasMessageContaining("prompts[].name");
    }

    @Test
    @DisplayName("callTool：listResources 缺 size 时 size 为 null")
    void listResourcesWithoutSizeKeepsNullSize() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{"resultType":"complete",
                  "resources":[{"uri":"file://x","name":"x"}]}}""");

        List<McpResourceDescriptor> resources = client.listResources(ENDPOINT, Map.of());

        assertThat(resources).singleElement().satisfies(resource ->
                assertThat(resource.size()).isNull());
    }

    @Test
    @DisplayName("exchange：异常带 cause 透传 IOException cause")
    void exchangeWrapsIOExceptionCause() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("net down"));

        assertThatThrownBy(() -> client.discover(ENDPOINT, Map.of()))
                .isInstanceOfSatisfying(McpHttpClientException.class, exception ->
                        assertThat(exception.getCause().getMessage()).contains("net down"));
    }

    @Test
    @DisplayName("callTool：content 不是数组抛客户端错误")
    void callToolRejectsNonArrayContent() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{
                  "content":"oops"}}""");

        assertThatThrownBy(() -> client.callTool(ENDPOINT, "x", Map.of(), Map.of()))
                .isInstanceOf(McpHttpClientException.class)
                .hasMessageContaining("result.content must be an array");
    }

    @Test
    @DisplayName("complete：解码 values + total + hasMore")
    void completeDecodesValues() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{"resultType":"complete",
                  "completion":{"values":["alpha","beta"],"total":2,"hasMore":true}}}""");

        McpCompletionResult completion = client.complete(
                ENDPOINT,
                Map.of("type", "ref/prompt"),
                "name", "a",
                Map.of(),
                null);

        assertThat(completion.values()).containsExactly("alpha", "beta");
        assertThat(completion.total()).isEqualTo(2);
        assertThat(completion.hasMore()).isTrue();
    }

    @Test
    @DisplayName("complete：values 字段非数组抛客户端错误")
    void completeRejectsNonArrayValues() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{"resultType":"complete",
                  "completion":{"values":"x"}}}""");

        assertThatThrownBy(() -> client.complete(
                ENDPOINT, Map.of("type", "ref/prompt"),
                "name", "a", Map.of(), null))
                .isInstanceOf(McpHttpClientException.class)
                .hasMessageContaining("completion.values must be an array");
    }

    @Test
    @DisplayName("非 2xx + error envelope：抛出携带协议错误的客户端异常")
    void nonOkWithErrorEnvelopeThrowsProtocolFailure() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        org.mockito.Mockito.doReturn(429).when(mockResponse).statusCode();
        org.mockito.Mockito.doReturn(
                "{\"jsonrpc\":\"2.0\",\"id\":\"x\",\"error\":{\"code\":-32000,\"message\":\"rate limited\",\"data\":{}}}")
                .when(mockResponse).body();
        org.mockito.Mockito.doReturn(HttpHeaders.of(
                Map.of("Retry-After", List.of("5")), (k, v) -> true))
                .when(mockResponse).headers();
        org.mockito.Mockito.doReturn(mockResponse)
                .when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        assertThatThrownBy(() -> client.discover(ENDPOINT, Map.of()))
                .isInstanceOfSatisfying(McpHttpClientException.class, exception -> {
                    assertThat(exception.httpStatus()).isEqualTo(429);
                    assertThat(exception.protocolError().orElseThrow().jsonRpcCode()).isEqualTo(-32000);
                    assertThat(exception.firstHeader("Retry-After")).contains("5");
                });
    }

    @Test
    @DisplayName("非 2xx 但 envelope 无 error：抛普通客户端错误（无 protocolError）")
    void nonOkWithoutErrorEnvelopeThrowsClientFailure() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 500, "<html>crash</html>");

        assertThatThrownBy(() -> client.discover(ENDPOINT, Map.of()))
                .isInstanceOfSatisfying(McpHttpClientException.class, exception -> {
                    assertThat(exception.httpStatus()).isEqualTo(500);
                    assertThat(exception.protocolError()).isEmpty();
                });
    }

    @Test
    @DisplayName("响应非 JSON：抛客户端错误")
    void nonJsonResponseThrowsClientFailure() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        org.mockito.Mockito.doReturn(200).when(mockResponse).statusCode();
        org.mockito.Mockito.doReturn("not-json").when(mockResponse).body();
        org.mockito.Mockito.doReturn(HttpHeaders.of(Map.of(), (k, v) -> true))
                .when(mockResponse).headers();
        org.mockito.Mockito.doReturn(mockResponse)
                .when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        assertThatThrownBy(() -> client.discover(ENDPOINT, Map.of()))
                .isInstanceOf(McpHttpClientException.class)
                .hasMessageContaining("not valid JSON-RPC");
    }

    @Test
    @DisplayName("2xx 但 result 不是对象：抛客户端错误")
    void okWithoutResultThrowsClientFailure() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x"}""");

        assertThatThrownBy(() -> client.discover(ENDPOINT, Map.of()))
                .isInstanceOf(McpHttpClientException.class)
                .hasMessageContaining("missing result");
    }

    @Test
    @DisplayName("SSE 响应提取最后一帧 data: 行作为最终 envelope")
    void sseResponsePicksLastDataFrame() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        String sseBody = "data: {\"intermediate\":\"ignore\"}\n"
                + "id:1\n"
                + "\n"
                + "data: {\"jsonrpc\":\"2.0\",\"id\":\"x\",\"result\":{"
                + "\"resultType\":\"complete\","
                + "\"supportedVersions\":[\"2026-07-28\"],"
                + "\"capabilities\":{},"
                + "\"ttlMs\":60000,"
                + "\"cacheScope\":\"public\"}}\n";
        org.mockito.Mockito.doReturn(200).when(mockResponse).statusCode();
        org.mockito.Mockito.doReturn(sseBody).when(mockResponse).body();
        org.mockito.Mockito.doReturn(HttpHeaders.of(Map.of(), (k, v) -> true))
                .when(mockResponse).headers();
        org.mockito.Mockito.doReturn(mockResponse)
                .when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        McpDiscoverResult result = client.discover(ENDPOINT, Map.of());

        assertThat(result.supportedVersions()).containsExactly("2026-07-28");
        assertThat(result.ttlMs()).isEqualTo(60000L);
    }

    @Test
    @DisplayName("HTTP IOException 被映射为客户端异常（httpStatus=0）")
    void ioExceptionBecomesClientFailure() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("connect refused"));

        assertThatThrownBy(() -> client.discover(ENDPOINT, Map.of()))
                .isInstanceOfSatisfying(McpHttpClientException.class, exception -> {
                    assertThat(exception.httpStatus()).isZero();
                    assertThat(exception.getCause()).isInstanceOf(IOException.class);
                });
    }

    @Test
    @DisplayName("HTTP 超时（HttpTimeoutException）同样映射为客户端异常")
    void httpTimeoutBecomesClientFailure() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("timed out"));

        assertThatThrownBy(() -> client.discover(ENDPOINT, Map.of()))
                .isInstanceOf(McpHttpClientException.class)
                .hasMessageContaining("HTTP request failed");
    }

    @Test
    @DisplayName("InterruptedException 重新设置中断标志并抛客户端异常")
    void interruptedExceptionRestoresInterruptFlag() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("interrupted"));
        try {
            assertThatThrownBy(() -> client.discover(ENDPOINT, Map.of()))
                    .isInstanceOf(McpHttpClientException.class)
                    .hasMessageContaining("interrupted");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("normalizeEndpoint：根路径自动追加 /mcp")
    void rootPathGetsMcpSegmentAppended() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{
                  "resultType":"complete",
                  "supportedVersions":["2026-07-28"],
                  "capabilities":{},
                  "ttlMs":300000,
                  "cacheScope":"public"}}""");

        client.discover(URI.create("https://mcp.example.com"), Map.of());

        org.mockito.ArgumentCaptor<HttpRequest> captor =
                org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().uri().getPath()).isEqualTo("/mcp");
    }

    @Test
    @DisplayName("normalizeEndpoint：带路径的 URI 保持不变")
    void explicitPathIsPreserved() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{
                  "resultType":"complete",
                  "supportedVersions":["2026-07-28"],
                  "capabilities":{},
                  "ttlMs":300000,
                  "cacheScope":"public"}}""");

        URI endpoint = URI.create("https://mcp.example.com/custom/v2/mcp");
        client.discover(endpoint, Map.of());

        org.mockito.ArgumentCaptor<HttpRequest> captor =
                org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().uri().getPath()).isEqualTo("/custom/v2/mcp");
    }

    @Test
    @DisplayName("protocolError 默认回退到 -32603 当 envelope.error 缺失 code")
    void protocolErrorFallsBackToInternalErrorCode() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 500, """
                {"jsonrpc":"2.0","id":"x","error":{"message":"oops"}}""");

        assertThatThrownBy(() -> client.discover(ENDPOINT, Map.of()))
                .isInstanceOfSatisfying(McpHttpClientException.class, exception ->
                        assertThat(exception.protocolError().orElseThrow().jsonRpcCode())
                                .isEqualTo(-32603));
    }

    @Test
    @DisplayName("callTool：inputResponses / requestState 被序列化到请求 body")
    void callToolEncodesMrtrFields() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        McpHttpToolClient client = new McpHttpToolClient(httpClient, Duration.ofSeconds(5));
        stubJson(httpClient, 200, """
                {"jsonrpc":"2.0","id":"x","result":{
                  "resultType":"complete","content":[]}}""");

        client.callTool(ENDPOINT, "ask", Map.of(),
                Map.of(), Map.of("a", 1), "state-1");

        org.mockito.ArgumentCaptor<HttpRequest> captor =
                org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        String body = readRequestBody(captor.getValue());
        assertThat(body).contains("\"inputResponses\":{\"a\":1}");
        assertThat(body).contains("\"requestState\":\"state-1\"");
    }

    // ---- helpers ----

    private static void stubJson(HttpClient httpClient, int status, String body) throws Exception {
        org.mockito.Mockito.doReturn(stringResponse(status, body))
                .when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private static HttpResponse<String> stringResponse(int status, String body) {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        org.mockito.Mockito.doReturn(status).when(mockResponse).statusCode();
        org.mockito.Mockito.doReturn(body).when(mockResponse).body();
        org.mockito.Mockito.doReturn(HttpHeaders.of(Map.of(), (k, v) -> true))
                .when(mockResponse).headers();
        return mockResponse;
    }

    private static String readRequestBody(HttpRequest request) throws InterruptedException, java.util.concurrent.ExecutionException {
        ByteArrayOutputStream collector = new ByteArrayOutputStream();
        CompletableFuture<Void> drained = new CompletableFuture<>();
        HttpRequest.BodyPublisher publisher = request.bodyPublisher()
                .orElseThrow(() -> new IllegalStateException("body missing"));
        publisher.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription upstream;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.upstream = subscription;
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                collector.writeBytes(chunk);
            }

            @Override
            public void onError(Throwable throwable) {
                drained.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                drained.complete(null);
            }
        });
        drained.get();
        return collector.toString(StandardCharsets.UTF_8);
    }
}
