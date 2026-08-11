package cn.richie696.component.mcp.security.oauth;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
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

/**
 * OAuth metadata discovery 客户端。网络访问与内部 DTO 映射集中在此处。
 *
 * <p>该类负责消费 RFC 9728（Protected Resource Metadata）与 RFC 8414/OIDC（Authorization Server
 * Metadata）两类 well-known 文档，把 HTTP/JSON 反序列化、字段校验、URI 安全策略一并封装，
 * 上层只关心"传入 metadata URI → 拿到归一化模型"。设计上做了三层权衡：</p>
 *
 * <ol>
 *   <li>框架中立：不绑定 Spring / Jakarta RestClient，直接复用 JDK {@link HttpClient}，方便在
 *       MCP starter、CLI 工具、测试桩中复用同一份代码；</li>
 *   <li>安全边界前置：所有入站 URI 在请求发出前都经 {@link McpOAuthUriPolicy} 校验，避免
 *       SSRF 攻击通过 metadata 端点回打到内网；</li>
 *   <li>严格字段校验：必填字段（{@code resource}、{@code issuer}）缺失或类型错误时立即抛
 *       {@link IllegalArgumentException}，把 AS 端契约错误挡在 MCP 流程启动阶段。</li>
 * </ol>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpOAuthMetadataClient {
    private final HttpClient httpClient;
    private final Duration timeout;
    private final McpOAuthUriPolicy uriPolicy;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /**
     * 便捷构造器：采用默认 HTTPS-only URI 策略。
     *
     * <p>为什么默认强制 HTTPS：metadata 端点通常承载 token 端点地址、issuer、受众资源等关键
     * 元数据，攻击者篡改后能诱导 client 把 token 发往恶意 AS。HTTPS-only 是 RFC 8414 §3.1 的
     * 推荐做法，也是 MCP 在公网场景的最低安全门槛。</p>
     *
     * @param httpClient 已配置好的 HTTP 客户端（建议复用、启用连接池）
     * @param timeout 单次请求超时
     */
    public McpOAuthMetadataClient(HttpClient httpClient, Duration timeout) {
        this(httpClient, timeout, McpOAuthUriPolicy.httpsOnly());
    }

    /**
     * 主构造器：允许注入自定义 URI 策略。
     *
     * <p>暴露该重载是为了让受信任的内部网络场景能注入更宽松的策略（如允许 http 内网地址），
     * 但默认仍走 HTTPS-only 防止误用。</p>
     *
     * @param httpClient HTTP 客户端（必填）
     * @param timeout 请求超时（必填，避免被慢响应拖死 caller）
     * @param uriPolicy URI 安全策略（必填）
     * @throws NullPointerException 任一参数为 null 时
     */
    public McpOAuthMetadataClient(
            HttpClient httpClient,
            Duration timeout,
            McpOAuthUriPolicy uriPolicy) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.uriPolicy = Objects.requireNonNull(uriPolicy, "uriPolicy");
    }

    /**
     * 拉取并解析 RFC 9728 Protected Resource Metadata。
     *
     * <p>把 RFC 9728 §2 规定的字段（{@code resource}、{@code authorization_servers}、
     * {@code scopes_supported}）映射为 {@link McpProtectedResourceMetadata}；同时把整张原始
     * JSON 作为 {@code extensions} 透传，方便上层在不修改 record 结构的情况下读取扩展字段。</p>
     *
     * @param metadataUri PRM 文档 URI（通常是 {@code /.well-known/oauth-protected-resource}）
     * @return 归一化后的 PRM 模型
     * @throws IllegalStateException HTTP 非 2xx 或响应体非 JSON 时
     * @throws IllegalArgumentException 必填字段缺失或类型错误时
     */
    public McpProtectedResourceMetadata fetchProtectedResourceMetadata(URI metadataUri) {
        Map<String, Object> raw = get(metadataUri);
        URI resource = uri(raw.get("resource"), "resource");
        List<URI> authorizationServers = uriList(raw.get("authorization_servers"), "authorization_servers");
        List<String> scopes = stringList(raw.get("scopes_supported"), "scopes_supported");
        return new McpProtectedResourceMetadata(resource, authorizationServers, scopes, raw);
    }

    /**
     * 拉取并解析 RFC 8414 / OIDC Authorization Server Metadata。
     *
     * <p>把 RFC 8414 §2 的必填与常用可选字段映射为 {@link McpAuthorizationServerMetadata}，
     其中 {@code issuer} 必填（{@link IllegalArgumentException} on null），其余 endpoint 字段
     可为空以兼容只暴露 token endpoint 的简化 AS 实现。</p>
     *
     * @param metadataUri AS metadata URI（通常是 {@code /.well-known/oauth-authorization-server}）
     * @return 归一化后的 AS metadata 模型
     * @throws IllegalStateException HTTP 非 2xx 或响应体非 JSON 时
     * @throws IllegalArgumentException 必填字段缺失或类型错误时
     */
    public McpAuthorizationServerMetadata fetchAuthorizationServerMetadata(URI metadataUri) {
        Map<String, Object> raw = get(metadataUri);
        return new McpAuthorizationServerMetadata(
                uri(raw.get("issuer"), "issuer"),
                optionalUri(raw.get("authorization_endpoint"), "authorization_endpoint"),
                optionalUri(raw.get("token_endpoint"), "token_endpoint"),
                optionalUri(raw.get("registration_endpoint"), "registration_endpoint"),
                stringList(raw.get("response_types_supported"), "response_types_supported"),
                stringList(raw.get("grant_types_supported"), "grant_types_supported"),
                stringList(raw.get("code_challenge_methods_supported"), "code_challenge_methods_supported"));
    }

    private Map<String, Object> get(URI uri) {
        uriPolicy.validate(uri);
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(uri).timeout(timeout).header("Accept", "application/json").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OAuth metadata endpoint returned HTTP " + response.statusCode());
            }
            Map<?, ?> raw = jsonMapper.readValue(response.body(), Map.class);
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, value) -> {
                if (key instanceof String text) {
                    result.put(text, value);
                }
            });
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OAuth metadata request interrupted", exception);
        } catch (IOException | JacksonException | ClassCastException exception) {
            throw new IllegalStateException("OAuth metadata response is not valid JSON", exception);
        }
    }

    private URI uri(Object value, String field) {
        URI uri = optionalUri(value, field);
        if (uri == null) {
            throw new IllegalArgumentException("OAuth metadata field " + field + " must be present");
        }
        return uri;
    }

    private URI optionalUri(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("OAuth metadata field " + field + " must be a URI string");
        }
        try {
            URI uri = URI.create(text);
            uriPolicy.validate(uri);
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid OAuth metadata URI in " + field, exception);
        }
    }

    private List<URI> uriList(Object value, String field) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("OAuth metadata field " + field + " must be an array");
        }
        List<URI> result = new ArrayList<>();
        for (Object entry : list) {
            result.add(uri(entry, field + "[]"));
        }
        return List.copyOf(result);
    }

    private List<String> stringList(Object value, String field) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("OAuth metadata field " + field + " must be an array");
        }
        List<String> result = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException("OAuth metadata field " + field + " contains an invalid value");
            }
            result.add(text);
        }
        return List.copyOf(result);
    }
}
