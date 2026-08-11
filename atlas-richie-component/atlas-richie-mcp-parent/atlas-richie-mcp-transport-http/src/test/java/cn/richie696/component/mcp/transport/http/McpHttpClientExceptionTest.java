package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.protocol.McpProtocolException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpHttpClientException} 的两种构造路径（带/不带响应头）、
 * 响应头归一化（{@code null} 替换为不可变空 Map）以及 {@link McpHttpClientException#firstHeader(String)}
 * 的大小写不敏感读头语义，这是 OAuth 401 等需要读取 {@code WWW-Authenticate} 头场景的核心 API。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpHttpClientException 客户端失败封装")
class McpHttpClientExceptionTest {

    @Test
    @DisplayName("不带响应头构造：responseHeaders 为不可变空 Map")
    void noHeadersConstructorProducesEmptyMap() {
        McpHttpClientException exception = new McpHttpClientException(
                "fail", 500, null, null);

        assertThat(exception.httpStatus()).isEqualTo(500);
        assertThat(exception.protocolError()).isEmpty();
        assertThat(exception.responseHeaders()).isEmpty();
        assertThat(exception.firstHeader("WWW-Authenticate")).isEmpty();
    }

    @Test
    @DisplayName("带响应头构造：null 头替换为不可变空 Map")
    void headersConstructorNormalizesNull() {
        McpHttpClientException exception = new McpHttpClientException(
                "fail", 401, null, null, null);

        assertThat(exception.responseHeaders()).isEmpty();
        assertThat(exception.firstHeader("WWW-Authenticate")).isEmpty();
    }

    @Test
    @DisplayName("带响应头构造：传入 Map 被复制为不可变视图")
    void headersAreCopiedToImmutableView() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("WWW-Authenticate", List.of("Bearer realm=\"x\""));
        headers.put("Retry-After", List.of("60"));
        McpHttpClientException exception = new McpHttpClientException(
                "fail", 401, null, null, headers);

        assertThat(exception.responseHeaders()).containsKey("WWW-Authenticate");
        try {
            exception.responseHeaders().put("X", List.of());
            assertThat(exception.responseHeaders()).containsKey("X");
        } catch (UnsupportedOperationException expected) {
            // 不可变视图允许写入失败即可
        }
        assertThat(exception.responseHeaders()).isInstanceOf(Map.class);
    }

    @Test
    @DisplayName("firstHeader：大小写不敏感匹配，返回首个值")
    void firstHeaderMatchesCaseInsensitive() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("www-authenticate", List.of("Bearer realm=\"primary\"", "Bearer realm=\"fallback\""));
        McpHttpClientException exception = new McpHttpClientException(
                "fail", 401, null, null, headers);

        assertThat(exception.firstHeader("WWW-Authenticate")).contains("Bearer realm=\"primary\"");
        assertThat(exception.firstHeader("WwW-Authenticate")).isPresent();
    }

    @Test
    @DisplayName("firstHeader：找不到时返回空 Optional")
    void firstHeaderReturnsEmptyWhenMissing() {
        Map<String, List<String>> headers = Map.of("Retry-After", List.of("30"));
        McpHttpClientException exception = new McpHttpClientException(
                "fail", 429, null, null, headers);

        assertThat(exception.firstHeader("WWW-Authenticate")).isEmpty();
    }

    @Test
    @DisplayName("protocolError：null 时返回空 Optional，存在时被包装")
    void protocolErrorReflectsNullity() {
        McpHttpClientException noProtocol = new McpHttpClientException("x", 500, null, null);
        McpProtocolException protocol = new McpProtocolException("E", -32603, "msg", Map.of());
        McpHttpClientException withProtocol = new McpHttpClientException("x", 400, protocol, null);

        assertThat(noProtocol.protocolError()).isEmpty();
        assertThat(withProtocol.protocolError()).contains(protocol);
    }
}
