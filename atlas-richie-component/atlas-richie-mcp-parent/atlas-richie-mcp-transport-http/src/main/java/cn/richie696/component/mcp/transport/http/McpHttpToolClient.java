package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.api.model.McpPromptContent;
import cn.richie696.component.mcp.api.model.McpPromptDescriptor;
import cn.richie696.component.mcp.api.model.McpCompletionResult;
import cn.richie696.component.mcp.api.model.McpResourceContent;
import cn.richie696.component.mcp.api.model.McpResourceDescriptor;
import cn.richie696.component.mcp.api.model.McpResourceTemplateDescriptor;
import cn.richie696.component.mcp.protocol.McpMetaKeys;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.discovery.McpDiscoverResult;
import cn.richie696.component.mcp.protocol.discovery.McpDiscoveryCodec;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcError;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 现代 MCP 协议的 HTTP 客户端传输适配器，负责把 HTTP / JSON-RPC / 协议元数据三件事都
 * 收敛在自己内部，让业务代码无需感知 MCP 协议以外的低层细节。
 *
 * <p>它是一个无状态、可复用的薄壳客户端，核心职责是：
 * <ul>
 *   <li>把 {@link cn.richie696.component.mcp.protocol.discovery.McpDiscoverResult}、
 *       {@link cn.richie696.component.mcp.protocol.discovery.McpDiscoveryCodec} 等协议层模型
 *       通过 HTTP envelope 暴露成方法调用，把任意一种 Spring 服务都不到的 MCP 服务呼叫成普通的 Java 方法。</li>
 *   <li>使用 JDK 内置 {@link HttpClient} 避免对 OkHttp / Apache HttpClient 的直接依赖，
 *       给上层一个零三方依赖的标准实现。</li>
 *   <li>把所有异常封包成 {@link McpHttpClientException}（屏蔽 wire 异常）。</li>
 * </ul></p>
 *
 * <p>典型使用模式：业务侧一般通过依赖注入构造一个实例，跨多次方法调用复用同一份连接池 /
 * 超时设置；MRTR 多轮交互通过 {@link McpMrtrCoordinator} 编排；调用方如需并发共享客户端
 * 实例则可放心使用——本类无状态变更方法，只会发出 HTTP 请求。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpHttpToolClient {
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final String clientName;
    private final String clientVersion;
    private final String protocolVersion;
    private final int maxPages;
    private final int maxItems;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /**
     * 默认构造：使用默认 HTTP 客户端、30 秒请求超时、SDK 自身标识以及 2026-07-28 协议版本。
     * 分页上限固定为 100 页或 10 000 项，足以覆盖大多数发现场景。
     */
    public McpHttpToolClient() {
        this(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                Duration.ofSeconds(30),
                "atlas-richie-mcp-client",
                "1.0.0",
                McpProtocolVersions.V_2026_07_28,
                100,
                10_000);
    }

    /**
     * 仅自定义 HTTP 客户端与请求超时，其他字段保持默认值。
     *
     * @param httpClient     自定义 {@link HttpClient}（可以是连接池化的全局共享实例）
     * @param requestTimeout 单次请求超时
     */
    public McpHttpToolClient(HttpClient httpClient, Duration requestTimeout) {
        this(httpClient, requestTimeout, "atlas-richie-mcp-client", "1.0.0",
                McpProtocolVersions.V_2026_07_28, 100, 10_000);
    }

    /**
     * 自定义 HTTP 客户端、超时与客户端标识。
     *
     * @param httpClient     自定义 HTTP 客户端
     * @param requestTimeout 单次请求超时
     * @param clientName     MCP 客户端名，会出现在请求 envelope 的 {@code _meta.clientInfo.name}
     * @param clientVersion  客户端版本
     */
    public McpHttpToolClient(
            HttpClient httpClient,
            Duration requestTimeout,
            String clientName,
            String clientVersion) {
        this(httpClient, requestTimeout, clientName, clientVersion, McpProtocolVersions.V_2026_07_28);
    }

    /**
     * 在上一构造基础上覆写协议版本；其余参数（HTTP 客户端、超时、分页上限等）使用默认。
     *
     * @param httpClient      自定义 HTTP 客户端
     * @param requestTimeout  单次请求超时
     * @param clientName      客户端名
     * @param clientVersion   客户端版本
     * @param protocolVersion 目标协议版本，必须属于当前支持集合
     */
    public McpHttpToolClient(
            HttpClient httpClient,
            Duration requestTimeout,
            String clientName,
            String clientVersion,
            String protocolVersion) {
        this(httpClient, requestTimeout, clientName, clientVersion, protocolVersion, 100, 10_000);
    }

    /**
     * 全参数构造。
     *
     * @param httpClient      自定义 HTTP 客户端（注入连接池、proxy、TLS 等）
     * @param requestTimeout  单次请求超时
     * @param clientName      客户端标识
     * @param clientVersion   客户端版本
     * @param protocolVersion 目标协议版本，必须在 {@link McpProtocolVersions#SUPPORTED} 中
     * @param maxPages        分页保护：单次 list 最多允许的页数
     * @param maxItems        分页保护：单次 list 累计最多允许的元素数
     * @throws IllegalArgumentException 协议版本不支持或分页上限非正数
     */
    public McpHttpToolClient(
            HttpClient httpClient,
            Duration requestTimeout,
            String clientName,
            String clientVersion,
            String protocolVersion,
            int maxPages,
            int maxItems) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.clientName = requiredClientValue(clientName, "clientName");
        this.clientVersion = requiredClientValue(clientVersion, "clientVersion");
        if (!McpProtocolVersions.SUPPORTED.contains(protocolVersion)) {
            throw new IllegalArgumentException("Unsupported MCP protocol version: " + protocolVersion);
        }
        this.protocolVersion = protocolVersion;
        if (maxPages < 1 || maxItems < 1) throw new IllegalArgumentException("MCP pagination limits must be positive");
        this.maxPages = maxPages;
        this.maxItems = maxItems;
    }

    /**
     * 返回一个共享底层 HTTP 客户端、改用指定协议版本的复制实例。用于运行时协商版本。
     *
     * @param version 目标协议版本
     * @return 新实例
     */
    public McpHttpToolClient forProtocolVersion(String version) {
        return new McpHttpToolClient(httpClient, requestTimeout, clientName, clientVersion, version, maxPages, maxItems);
    }

    /**
     * @return 当前客户端使用的 MCP 协议版本
     */
    public String protocolVersion() {
        return protocolVersion;
    }

    /**
     * 通过 {@code server/discover} 方法获取远端 MCP 服务的发现结果。
     *
     * @param endpoint 远端服务端点 URI，{@code null} 或根路径会被规范为 {@code /mcp}
     * @param headers  自定义 HTTP 头（如鉴权、租户标识等）
     * @return 解析后的 {@link cn.richie696.component.mcp.protocol.discovery.McpDiscoverResult}
     * @throws McpHttpClientException 协议错误或网络异常时
     */
    public McpDiscoverResult discover(URI endpoint, Map<String, String> headers) {
        return new McpDiscoveryCodec().decodeResult(exchange(endpoint, McpDiscoveryCodec.METHOD, Map.of(), headers));
    }

    /**
     * 列举远端 MCP 服务暴露的所有工具。内部使用 {@code tools/list} + cursor 自翻页直到穷尽。
     *
     * <p>同时启用 {@code maxPages} 与 {@code maxItems} 两层防御：恶意/错误实现的 server
     * 无法通过死循环或过大的列表耗尽客户端内存。</p>
     *
     * @param endpoint 远端服务端点 URI
     * @param headers  自定义 HTTP 头
     * @return 不可变工具列表，按页发现顺序拼接
     * @throws McpHttpClientException {@code tools/list} 返回结构非法或超出分页上限
     */
    public List<McpRemoteTool> listTools(URI endpoint, Map<String, String> headers) {
        List<McpRemoteTool> resolved = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            if (++pages > maxPages) throw clientFailure("MCP tools/list exceeded maxPages", 200, null, null);
            Map<String, Object> result = exchange(endpoint, "tools/list", pageParams(cursor), headers);
            Object rawTools = result.get("tools");
            if (!(rawTools instanceof List<?> tools)) {
                throw clientFailure("MCP tools/list result.tools must be an array", 200, null, null);
            }
            if (resolved.size() > maxItems) throw clientFailure("MCP tools/list exceeded maxItems", 200, null, null);
            for (Object rawTool : tools) {
                if (!(rawTool instanceof Map<?, ?> raw)) {
                    throw clientFailure("MCP tools/list contains a non-object tool", 200, null, null);
                }
                resolved.add(new McpRemoteTool(
                        requiredString(raw.get("name"), "tools[].name"),
                        optionalString(raw.get("title"), "tools[].title"),
                        optionalString(raw.get("description"), "tools[].description"),
                        object(raw.get("inputSchema"), "tools[].inputSchema"),
                        object(raw.get("outputSchema"), "tools[].outputSchema"),
                        object(raw.get("annotations"), "tools[].annotations")));
            }
            cursor = nextCursor(result);
        } while (cursor != null);
        return List.copyOf(resolved);
    }

    /**
     * 单轮调用工具方法（无 MRTR 上下文）。
     *
     * @param endpoint  远端端点 URI
     * @param toolName  工具名
     * @param arguments 调用参数，允许为 null（被视作空 Map）
     * @param headers   自定义 HTTP 头
     * @return 远端工具响应
     * @throws McpHttpClientException 网络/协议/参数错误
     */
    public McpToolResponse callTool(
            URI endpoint,
            String toolName,
            Map<String, Object> arguments,
            Map<String, String> headers) {
        return callTool(endpoint, toolName, arguments, headers, Map.of(), null);
    }

    /**
     * 支持 MRTR 多轮交互的工具调用重载。
     *
     * <p>首次调用时 {@code inputResponses} 为空 Map、{@code requestState} 为 null。
     * 在多轮场景中，根据远端返回的 {@code resultType=input_required} 用前一轮的
     * {@code requestState}、新的输入再次调用本方法直到 {@code resultType=complete}。</p>
     *
     * @param endpoint         远端端点
     * @param toolName         工具名
     * @param arguments        入参（首轮用）
     * @param headers          自定义 HTTP 头
     * @param inputResponses   对 {@code input_required} 的回复映射（首轮传空 Map）
     * @param requestState     MRTR 会话状态，由远端在 {@code input_required} 时签发
     * @return 工具响应，可能为 {@code input_required} / {@code complete} / {@code canceled} 等
     * @throws McpHttpClientException 协议或网络错误
     */
    public McpToolResponse callTool(
            URI endpoint,
            String toolName,
            Map<String, Object> arguments,
            Map<String, String> headers,
            Map<String, Object> inputResponses,
            String requestState) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", toolName);
        params.put("arguments", arguments == null ? Map.of() : arguments);
        if (inputResponses != null && !inputResponses.isEmpty()) params.put("inputResponses", inputResponses);
        if (requestState != null && !requestState.isBlank()) params.put("requestState", requestState);
        Map<String, Object> result = exchange(
                endpoint,
                "tools/call",
                params,
                headers);
        Object contentValue = result.get("content");
        List<Map<String, Object>> content = objectList(contentValue, "tools/call result.content");
        return new McpToolResponse(
                content,
                result.get("structuredContent"),
                Boolean.TRUE.equals(result.get("isError")),
                optionalString(result.get("resultType"), "tools/call result.resultType"),
                object(result.get("inputRequests"), "tools/call result.inputRequests"),
                optionalString(result.get("requestState"), "tools/call result.requestState"));
    }

    /**
     * 列举远端 MCP 服务暴露的所有资源。
     *
     * @param endpoint 远端端点
     * @param headers  自定义 HTTP 头
     * @return 不可变资源描述列表
     * @throws McpHttpClientException 协议错误或分页越界
     */
    public List<McpResourceDescriptor> listResources(URI endpoint, Map<String, String> headers) {
        List<McpResourceDescriptor> resolved = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            if (++pages > maxPages) throw clientFailure("MCP resources/list exceeded maxPages", 200, null, null);
            Map<String, Object> result = exchange(endpoint, "resources/list", pageParams(cursor), headers);
            Object rawResources = result.get("resources");
            if (!(rawResources instanceof List<?> resources)) {
                throw clientFailure("MCP resources/list result.resources must be an array", 200, null, null);
            }
            if (resolved.size() > maxItems) throw clientFailure("MCP resources/list exceeded maxItems", 200, null, null);
            for (Object rawValue : resources) {
                Map<String, Object> raw = object(rawValue, "resources[]");
                resolved.add(new McpResourceDescriptor(
                        requiredString(raw.get("uri"), "resources[].uri"),
                        requiredString(raw.get("name"), "resources[].name"),
                        optionalString(raw.get("title"), "resources[].title"),
                        optionalString(raw.get("description"), "resources[].description"),
                        optionalString(raw.get("mimeType"), "resources[].mimeType"),
                        raw.get("size") instanceof Number number ? number.longValue() : null,
                        optionalObjectList(raw.get("icons"), "resources[].icons"),
                        object(raw.get("annotations"), "resources[].annotations")));
            }
            cursor = nextCursor(result);
        } while (cursor != null);
        return List.copyOf(resolved);
    }

    /**
     * 列举远端 MCP 服务暴露的资源模板（URI 模板 + 形参）。
     *
     * @param endpoint 远端端点
     * @param headers  自定义 HTTP 头
     * @return 不可变资源模板描述列表
     * @throws McpHttpClientException 协议错误或分页越界
     */
    public List<McpResourceTemplateDescriptor> listResourceTemplates(
            URI endpoint,
            Map<String, String> headers) {
        List<McpResourceTemplateDescriptor> resolved = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            if (++pages > maxPages) throw clientFailure("MCP resourceTemplates/list exceeded maxPages", 200, null, null);
            Map<String, Object> result = exchange(
                    endpoint, "resources/templates/list", pageParams(cursor), headers);
            Object rawTemplates = result.get("resourceTemplates");
            if (!(rawTemplates instanceof List<?> templates)) {
                throw clientFailure(
                        "MCP resources/templates/list result.resourceTemplates must be an array",
                        200, null, null);
            }
            if (resolved.size() > maxItems) throw clientFailure("MCP resourceTemplates/list exceeded maxItems", 200, null, null);
            for (Object rawValue : templates) {
                Map<String, Object> raw = object(rawValue, "resourceTemplates[]");
                resolved.add(new McpResourceTemplateDescriptor(
                        requiredString(raw.get("uriTemplate"), "resourceTemplates[].uriTemplate"),
                        requiredString(raw.get("name"), "resourceTemplates[].name"),
                        optionalString(raw.get("title"), "resourceTemplates[].title"),
                        optionalString(raw.get("description"), "resourceTemplates[].description"),
                        optionalString(raw.get("mimeType"), "resourceTemplates[].mimeType"),
                        optionalObjectList(raw.get("icons"), "resourceTemplates[].icons"),
                        object(raw.get("annotations"), "resourceTemplates[].annotations")));
            }
            cursor = nextCursor(result);
        } while (cursor != null);
        return List.copyOf(resolved);
    }

    /**
     * 通过 {@code resources/read} 读取远端具体资源内容。
     *
     * @param endpoint 远端端点
     * @param uri      资源 URI（必须能被远端解释为已注册的资源）
     * @param headers  自定义 HTTP 头
     * @return 包含若干 {@link java.util.Map} 内容项的资源内容
     * @throws McpHttpClientException 协议错误或响应字段非法
     */
    public McpResourceContent readResource(
            URI endpoint,
            String uri,
            Map<String, String> headers) {
        Map<String, Object> result = exchange(endpoint, "resources/read", Map.of("uri", uri), headers);
        return new McpResourceContent(objectList(result.get("contents"), "resources/read result.contents"));
    }

    /**
     * 列举远端 MCP 服务暴露的提示模板。
     *
     * @param endpoint 远端端点
     * @param headers  自定义 HTTP 头
     * @return 不可变提示模板列表
     * @throws McpHttpClientException 协议错误或分页越界
     */
    public List<McpPromptDescriptor> listPrompts(URI endpoint, Map<String, String> headers) {
        List<McpPromptDescriptor> resolved = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            if (++pages > maxPages) throw clientFailure("MCP prompts/list exceeded maxPages", 200, null, null);
            Map<String, Object> result = exchange(endpoint, "prompts/list", pageParams(cursor), headers);
            Object rawPrompts = result.get("prompts");
            if (!(rawPrompts instanceof List<?> prompts)) {
                throw clientFailure("MCP prompts/list result.prompts must be an array", 200, null, null);
            }
            if (resolved.size() > maxItems) throw clientFailure("MCP prompts/list exceeded maxItems", 200, null, null);
            for (Object rawValue : prompts) {
                Map<String, Object> raw = object(rawValue, "prompts[]");
                resolved.add(new McpPromptDescriptor(
                        requiredString(raw.get("name"), "prompts[].name"),
                        optionalString(raw.get("title"), "prompts[].title"),
                        optionalString(raw.get("description"), "prompts[].description"),
                        optionalObjectList(raw.get("arguments"), "prompts[].arguments")));
            }
            cursor = nextCursor(result);
        } while (cursor != null);
        return List.copyOf(resolved);
    }

    /**
     * 通过 {@code prompts/get} 获取远端提示填充结果（标准 chat 消息列表）。
     *
     * @param endpoint   远端端点
     * @param name       提示名
     * @param arguments  模板形参
     * @param headers    自定义 HTTP 头
     * @return 提示消息内容，{@code description} 为空时返回空字符串
     * @throws McpHttpClientException 协议错误或响应字段非法
     */
    public McpPromptContent getPrompt(
            URI endpoint,
            String name,
            Map<String, Object> arguments,
            Map<String, String> headers) {
        Map<String, Object> result = exchange(
                endpoint,
                "prompts/get",
                Map.of("name", name, "arguments", arguments == null ? Map.of() : arguments),
                headers);
        return new McpPromptContent(
                optionalString(result.get("description"), "prompts/get result.description"),
                objectList(result.get("messages"), "prompts/get result.messages"));
    }

    /**
     * 补全给定 ref/argument 名称/值的可能候选项。对应 {@code completion/complete}。
     *
     * @param endpoint          远端端点
     * @param reference         协议层 {@code ref} 对象（{@code type}/{@code uri} 等）
     * @param argumentName      要补全的参数名
     * @param value             当前用户已输入的部分值
     * @param headers           自定义 HTTP 头
     * @param contextArguments 额外的上下文参数，{@code null}/空会被序列化为空
     * @return 补全结果（候选值列表、可选总数、是否还有更多）
     * @throws McpHttpClientException 协议错误或响应字段非法
     */
    public McpCompletionResult complete(
            URI endpoint,
            Map<String, Object> reference,
            String argumentName,
            String value,
            Map<String, String> headers,
            Map<String, String> contextArguments) {
        Map<String, Object> context = contextArguments == null || contextArguments.isEmpty()
                ? Map.of() : Map.of("arguments", contextArguments);
        Map<String, Object> result = exchange(endpoint, "completion/complete", Map.of(
                "ref", reference == null ? Map.of() : reference,
                "argument", Map.of("name", argumentName, "value", value),
                "context", context), headers);
        Map<String, Object> completion = object(result.get("completion"), "completion");
        Object values = completion.get("values");
        if (!(values instanceof List<?> list)) {
            throw clientFailure("completion.values must be an array", 200, null, null);
        }
        List<String> textValues = list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        return new McpCompletionResult(textValues,
                completion.get("total") instanceof Number number ? number.intValue() : null,
                Boolean.TRUE.equals(completion.get("hasMore")));
    }

    /**
     * 实际发出 MCP JSON-RPC 请求的通用方法：构造 envelope → POST → 解析 envelope（含 SSE）→ 校验 result/error。
     *
     * <p>SSE 响应处理：在响应体含 {@code data:} 时反向（从末尾向前）查找最近的 {@code data:} 行
     * 并将其作为 JSON 解析对象；这是因为 SSE 可在数据帧后还附着一段注释或心跳，保留末尾最终
     * 一次逻辑事件是协议安全的选择。</p>
     *
     * @param endpoint     请求目标
     * @param method       JSON-RPC method 名
     * @param params       业务参数
     * @param extraHeaders 额外 HTTP 头（自动覆盖默认头）
     * @return envelope.result 字段的 Map 视图
     * @throws McpHttpClientException 协议/网络/HTTP 错误
     */
    private Map<String, Object> exchange(
            URI endpoint,
            String method,
            Map<String, Object> params,
            Map<String, String> extraHeaders) {
        URI requestUri = normalizeEndpoint(endpoint);
        String id = UUID.randomUUID().toString();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(McpMetaKeys.PROTOCOL_VERSION, protocolVersion);
        metadata.put(McpMetaKeys.CLIENT_INFO, Map.of("name", clientName, "version", clientVersion));
        metadata.put(McpMetaKeys.CLIENT_CAPABILITIES, Map.of());
        Map<String, Object> bodyParams = new LinkedHashMap<>(params);
        bodyParams.put("_meta", metadata);
        Map<String, Object> body = Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", bodyParams);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(requestUri)
                    .timeout(requestTimeout)
                    .header(McpHttpHeaders.CONTENT_TYPE, "application/json")
                    .header(McpHttpHeaders.ACCEPT, "application/json, text/event-stream")
                    .header(McpHttpHeaders.PROTOCOL_VERSION, protocolVersion)
                    .header(McpHttpHeaders.METHOD, method);
            if (method.equals("tools/call")) {
                builder.header(McpHttpHeaders.NAME, String.valueOf(params.get("name")));
            }
            if (extraHeaders != null) {
                extraHeaders.forEach(builder::header);
            }
            HttpResponse<String> response = httpClient.send(
                    builder.POST(HttpRequest.BodyPublishers.ofString(writeJson(body))).build(),
                    HttpResponse.BodyHandlers.ofString());
            Map<String, Object> envelope = readEnvelope(response.body(), response.statusCode(), response.headers().map());
            Object error = envelope.get("error");
            if (error instanceof Map<?, ?> rawError) {
                throw protocolFailure(response.statusCode(), rawError, response.headers().map());
            }
            Object result = envelope.get("result");
            if (!(result instanceof Map<?, ?> rawResult)) {
                throw clientFailure("MCP response is missing result", response.statusCode(), null, null);
            }
            Map<String, Object> typed = new LinkedHashMap<>();
            rawResult.forEach((key, value) -> {
                if (key instanceof String text) {
                    typed.put(text, value);
                }
            });
            return typed;
        } catch (McpHttpClientException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw clientFailure("MCP HTTP request interrupted", 0, null, exception);
        } catch (Exception exception) {
            throw clientFailure("MCP HTTP request failed", 0, null, exception);
        }
    }

    /**
     * 解析 JSON-RPC envelope。当响应是 SSE 时尝试提取最后一条 {@code data:} 行；
     * 否则按普通 JSON 解析；遇到非 2xx 状态码时根据 envelope 是否含 {@code error} 字段决定
     * 是抛出协议错误还是抛出普通客户端错误。
     *
     * @param body             响应文本
     * @param status           HTTP 状态码
     * @param responseHeaders  响应头 Map（用于错误报告）
     * @return envelope 中的 {@code result} 与 {@code error} 键
     * @throws McpHttpClientException JSON 解析失败、状态码异常、协议错误
     */
    private Map<String, Object> readEnvelope(String body, int status, Map<String, List<String>> responseHeaders) {
        String json = body;
        if (body != null && body.contains("data:")) {
            String[] lines = body.split("\\R");
            for (int index = lines.length - 1; index >= 0; index--) {
                if (lines[index].startsWith("data:")) {
                    json = lines[index].substring("data:".length()).strip();
                    break;
                }
            }
        }
        try {
            Map<?, ?> raw = jsonMapper.readValue(json, Map.class);
            Map<String, Object> envelope = new LinkedHashMap<>();
            raw.forEach((key, value) -> {
                if (key instanceof String text) {
                    envelope.put(text, value);
                }
            });
            if (status < 200 || status >= 300) {
                Object error = envelope.get("error");
                if (error instanceof Map<?, ?> rawError) {
                    throw protocolFailure(status, rawError, responseHeaders);
                }
                throw clientFailure("MCP HTTP server returned status " + status, status, null, null, responseHeaders);
            }
            return envelope;
        } catch (McpHttpClientException exception) {
            throw exception;
        } catch (JacksonException | ClassCastException exception) {
            throw clientFailure("MCP HTTP response is not valid JSON-RPC", status, null, exception, responseHeaders);
        }
    }

    /**
     * 把 envelope 中的 {@code error} 字段组装为 {@link McpHttpClientException}。
     *
     * <p>JSON-RPC 协议错误码默认回退到 {@code -32603 Internal Error} 以保证 {@link McpHttpClientException#protocolError()}
     * 总能携带一个有效 code，即使远端协议破坏性缺失 {@code code} 字段。</p>
     *
     * @param status          HTTP 状态码
     * @param rawError        envelope.error 原始 Map
     * @param responseHeaders 响应头
     * @return 已经携带协议错误的客户端异常
     */
    private McpHttpClientException protocolFailure(
            int status,
            Map<?, ?> rawError,
            Map<String, List<String>> responseHeaders) {
        int code = rawError.get("code") instanceof Number number ? number.intValue() : -32603;
        String message = rawError.get("message") instanceof String text ? text : "MCP protocol error";
        McpProtocolException protocol = new McpProtocolException(
                "MCP_REMOTE_PROTOCOL_ERROR", code, message, object(rawError.get("data"), "error.data"));
        return clientFailure(message, status, protocol, null, responseHeaders);
    }

    /**
     * 将端点 URI 规范化：若 URI 没有路径或只有 {@code "/}，自动添加 {@code /mcp} 段。
     * 这一约定的存在是为了兼容"用户传入的是域名根"这一常见误用，遵循 MCP 协议默认路径。
     *
     * @param endpoint 用户传入的 URI
     * @return 规范化后的 URI
     * @throws McpHttpClientException 路径拼接失败（极少见）
     */
    private URI normalizeEndpoint(URI endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        String path = endpoint.getPath();
        if (path == null || path.isBlank() || path.equals("/")) {
            String normalizedPath = (path == null || path.isBlank() ? "" : path) + "/mcp";
            try {
                return new URI(endpoint.getScheme(), endpoint.getUserInfo(), endpoint.getHost(), endpoint.getPort(),
                        normalizedPath, endpoint.getQuery(), endpoint.getFragment());
            } catch (Exception exception) {
                throw clientFailure("Invalid MCP endpoint", 0, null, exception);
            }
        }
        return endpoint;
    }

    /**
     * 触发 Jackson 序列化写出 envelope body。
     *
     * @param value envelope Map
     * @return JSON 文本
     * @throws JacksonException 序列化失败
     */
    private String writeJson(Object value) throws JacksonException {
        return jsonMapper.writeValueAsString(value);
    }

    /**
     * 把 {@link Object} 安全窄化为 {@link Map}{@code <String, Object>}：null 视为空 Map，
     * 类型不匹配抛出客户端错误。{@link LinkedHashMap} 用于保留 JSON 字段顺序以便诊断输出。
     *
     * @param value 原始值
     * @param field 出错报告使用的字段名
     * @return 只读 Map 视图
     */
    private Map<String, Object> object(Object value, String field) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> raw)) {
            throw clientFailure(field + " must be an object", 200, null, null);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, entry) -> {
            if (key instanceof String text) {
                result.put(text, entry);
            }
        });
        return result;
    }

    /**
     * 构造 cursor 分页参数：第一页时 cursor=null ⇒ 空 Map，后续页带上 {@code params.cursor}。
     *
     * @param cursor 上一次响应的 nextCursor，可能为 null
     * @return 适配 MCP {@code list} 方法的 params 子集
     */
    private Map<String, Object> pageParams(String cursor) {
        return cursor == null ? Map.of() : Map.of("cursor", cursor);
    }

    /**
     * 从 list 响应中提取 nextCursor；缺失或 null 表示已到末页。
     *
     * @param result list 响应 result Map
     * @return 非空 cursor 字符串；末页时返回 null
     */
    private String nextCursor(Map<String, Object> result) {
        Object value = result.get("nextCursor");
        return value == null ? null : requiredString(value, "nextCursor");
    }

    /**
     * 把 {@link Object} 严格窄化为 {@code List<Map<String, Object>>}；每项非法同样抛错。
     *
     * @param value 原始值
     * @param field 字段路径，便于排错
     * @return 不可变 List
     */
    private List<Map<String, Object>> objectList(Object value, String field) {
        if (!(value instanceof List<?> raw)) {
            throw clientFailure(field + " must be an array", 200, null, null);
        }
        List<Map<String, Object>> result = new ArrayList<>(raw.size());
        for (Object entry : raw) {
            result.add(object(entry, field + "[]"));
        }
        return List.copyOf(result);
    }

    /**
     * {@link #objectList(Object, String)} 的 nullable 版本：null 替换为 {@link List#of()}。
     */
    private List<Map<String, Object>> optionalObjectList(Object value, String field) {
        return value == null ? List.of() : objectList(value, field);
    }

    /**
     * 构造器级别的不可空白校验：客户端名/版本不允许为空。
     */
    private String requiredClientValue(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    /**
     * 必须为非空字符串；空字符串/null/非 String 类型均抛客户端错误。
     */
    private String requiredString(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw clientFailure(field + " must be a non-blank string", 200, null, null);
        }
        return text;
    }

    /**
     * 可选字符串：null 返回 ""。
     */
    private String optionalString(Object value, String field) {
        return value == null ? "" : requiredString(value, field);
    }

    /**
     * 构造 {@link McpHttpClientException} 的便捷重载：忽略响应头。
     */
    private McpHttpClientException clientFailure(String message, int status, McpProtocolException error, Throwable cause) {
        return new McpHttpClientException(message, status, error, cause);
    }

    /**
     * 构造携带响应头的 {@link McpHttpClientException}。
     */
    private McpHttpClientException clientFailure(
            String message,
            int status,
            McpProtocolException error,
            Throwable cause,
            Map<String, List<String>> responseHeaders) {
        return new McpHttpClientException(message, status, error, cause, responseHeaders);
    }
}
