package cn.richie696.component.mcp.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpClientRequest} 紧凑构造器对端点协议、Header 名/值的合法性校验
 * 与不可变拷贝，以及 {@link McpClientRequest#endpointCacheKey()} 的端点指纹语义。
 */
@DisplayName("McpClientRequest 客户端请求值对象")
class McpClientRequestTest {

    @Test
    @DisplayName("http/https 端点被接受；serverId 缺省时回落到端点字符串")
    void shouldAcceptHttpAndHttpsEndpoints() {
        McpClientRequest http = new McpClientRequest(URI.create("http://mcp.local/x"), Map.of("h", "v"));
        McpClientRequest https = new McpClientRequest("svc", URI.create("https://mcp.local/y"), Map.of("h", "v"));

        assertThat(http.serverId()).isEqualTo("http://mcp.local/x");
        assertThat(https.serverId()).isEqualTo("svc");
        assertThat(http.endpoint()).isEqualTo(URI.create("http://mcp.local/x"));
        assertThat(https.headers()).containsExactly(entry("h", "v"));
    }

    @Test
    @DisplayName("大小写不敏感的 http/https 协议")
    void shouldAcceptSchemeCaseInsensitively() {
        McpClientRequest req = new McpClientRequest(URI.create("HTTPS://mcp.local"), Map.of());
        assertThat(req.endpoint().getScheme()).isEqualToIgnoringCase("HTTPS");
    }

    @Test
    @DisplayName("非 http/https 端点抛 IllegalArgumentException")
    void shouldRejectNonHttpEndpoints() {
        URI file = URI.create("file:///tmp/x");
        assertThatThrownBy(() -> new McpClientRequest(file, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http or https");
    }

    @Test
    @DisplayName("null 端点抛 NullPointerException")
    void shouldRejectNullEndpoint() {
        assertThatThrownBy(() -> new McpClientRequest(null, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("endpoint");
    }

    @Test
    @DisplayName("空 / 空白 serverId 回落为端点字符串")
    void shouldFallbackBlankServerId() {
        McpClientRequest blank = new McpClientRequest("   ", URI.create("https://mcp.local"), Map.of());
        McpClientRequest empty = new McpClientRequest("", URI.create("https://mcp.local"), Map.of());
        McpClientRequest nullId = new McpClientRequest(null, URI.create("https://mcp.local"), Map.of());

        assertThat(blank.serverId()).isEqualTo("https://mcp.local");
        assertThat(empty.serverId()).isEqualTo("https://mcp.local");
        assertThat(nullId.serverId()).isEqualTo("https://mcp.local");
    }

    @Test
    @DisplayName("header 名为空时抛 IllegalArgumentException")
    void shouldRejectBlankHeaderName() {
        Map<String, String> bad = new LinkedHashMap<>();
        bad.put("", "value");

        assertThatThrownBy(() -> new McpClientRequest(URI.create("https://mcp.local"), bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header name");
    }

    @Test
    @DisplayName("header 值为 null 时被丢弃")
    void shouldDropNullHeaderValues() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-A", "1");
        headers.put("X-B", null);
        headers.put("X-C", "3");

        McpClientRequest req = new McpClientRequest(URI.create("https://mcp.local"), headers);

        assertThat(req.headers()).containsOnlyKeys("X-A", "X-C");
    }

    @Test
    @DisplayName("headers 为 null 时回落到空 Map")
    void shouldFallbackHeadersToEmpty() {
        McpClientRequest req = new McpClientRequest(URI.create("https://mcp.local"), null);
        assertThat(req.headers()).isEmpty();
    }

    @Test
    @DisplayName("header 集合不可变：构造后修改外部 Map 不影响内部状态")
    void shouldDefensivelyCopyHeaders() {
        Map<String, String> mutable = new LinkedHashMap<>();
        mutable.put("X-A", "1");
        McpClientRequest req = new McpClientRequest(URI.create("https://mcp.local"), mutable);

        mutable.put("X-B", "2");

        assertThat(req.headers()).containsOnlyKeys("X-A");
        assertThatThrownBy(() -> req.headers().put("X-Z", "z"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("endpointCacheKey 仅基于端点生成，与凭据/headers 无关")
    void shouldDeriveCacheKeyFromEndpoint() {
        McpClientRequest a = new McpClientRequest(URI.create("https://mcp.local/x"), Map.of("X-A", "1"));
        McpClientRequest b = new McpClientRequest(URI.create("https://mcp.local/x"), Map.of("X-B", "2"));

        assertThat(a.endpointCacheKey()).isEqualTo("https://mcp.local/x");
        assertThat(a.endpointCacheKey()).isEqualTo(b.endpointCacheKey());
    }

    private static Map.Entry<String, String> entry(String key, String value) {
        return Map.entry(key, value);
    }
}
