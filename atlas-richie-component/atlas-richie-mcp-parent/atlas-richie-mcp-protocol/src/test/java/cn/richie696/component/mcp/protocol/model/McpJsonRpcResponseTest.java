package cn.richie696.component.mcp.protocol.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpJsonRpcResponse} 严格遵循 JSON-RPC 2.0 互斥约束：
 * {@code result} 与 {@code error} 二者必有其一、不可兼得；同时缺失或同时存在都抛
 * {@link IllegalArgumentException}；{@code null} result 在构造期归一为不可变空 Map。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpJsonRpcResponse 响应 result/error 互斥约束")
class McpJsonRpcResponseTest {

    @Test
    @DisplayName("仅 result 合法")
    void resultOnlyIsAllowed() {
        Map<String, Object> payload = Map.of("ok", true);
        McpJsonRpcResponse response = new McpJsonRpcResponse("2.0", 1, payload, null);

        assertThat(response.result()).containsEntry("ok", true);
        assertThat(response.error()).isNull();
    }

    @Test
    @DisplayName("仅 error 合法")
    void errorOnlyIsAllowed() {
        McpJsonRpcError error = new McpJsonRpcError(-32600, "invalid", Map.of());
        McpJsonRpcResponse response = new McpJsonRpcResponse("2.0", 1, null, error);

        assertThat(response.error()).isSameAs(error);
        assertThat(response.result()).isEmpty();
    }

    @Test
    @DisplayName("result 与 error 同时存在必须拒绝")
    void bothSetIsRejected() {
        McpJsonRpcError error = new McpJsonRpcError(-32600, "invalid", Map.of());
        Map<String, Object> payload = Map.of("ok", true);

        assertThatThrownBy(() -> new McpJsonRpcResponse("2.0", 1, payload, error))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one of result or error");
    }

    @Test
    @DisplayName("result 与 error 同时缺失必须拒绝")
    void bothMissingIsRejected() {
        assertThatThrownBy(() -> new McpJsonRpcResponse("2.0", 1, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one of result or error");
    }

    @Test
    @DisplayName("null result 归一为不可变空 Map")
    void nullResultNormalizesToEmpty() {
        McpJsonRpcError error = new McpJsonRpcError(-32600, "invalid", Map.of());
        McpJsonRpcResponse response = new McpJsonRpcResponse("2.0", 1, null, error);

        assertThat(response.result()).isEmpty();
        assertThatThrownBy(() -> response.result().put("k", "v"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("result 入参被拷贝为不可变副本，外部修改不影响响应")
    void resultInputIsImmutableSnapshot() {
        Map<String, Object> mutable = new HashMap<>();
        mutable.put("a", 1);
        McpJsonRpcResponse response = new McpJsonRpcResponse("2.0", 1, mutable, null);

        mutable.put("b", 2);

        assertThat(response.result()).containsOnlyKeys("a");
    }
}
