package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpValidatedHttpRequest} 作为 record 的常规契约：访问器、equals 与 hashCode。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpValidatedHttpRequest 已校验请求 record")
class McpValidatedHttpRequestTest {

    @Test
    @DisplayName("访问器回传构造时的字段")
    void accessorsReturnConstructorFields() {
        McpJsonRpcRequest message = new McpJsonRpcRequest("2.0", 1, "tools/list", Map.of());
        McpValidatedHttpRequest request = new McpValidatedHttpRequest("2026-07-28", message);

        assertThat(request.protocolVersion()).isEqualTo("2026-07-28");
        assertThat(request.message()).isSameAs(message);
    }

    @Test
    @DisplayName("相等的两个 record 返回相等 hashCode")
    void equalsAndHashCodeReflectFields() {
        McpJsonRpcRequest message = new McpJsonRpcRequest("2.0", 2, "tools/call", Map.of("name", "x"));
        McpValidatedHttpRequest a = new McpValidatedHttpRequest("2026-07-28", message);
        McpValidatedHttpRequest b = new McpValidatedHttpRequest("2026-07-28", message);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    @DisplayName("协议版本不同则不相等")
    void differentProtocolVersionBreaksEquality() {
        McpJsonRpcRequest message = new McpJsonRpcRequest("2.0", 3, "ping", Map.of());
        McpValidatedHttpRequest a = new McpValidatedHttpRequest("2026-07-28", message);
        McpValidatedHttpRequest b = new McpValidatedHttpRequest("2025-11-25", message);

        assertThat(a).isNotEqualTo(b);
    }
}
