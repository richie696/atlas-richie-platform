package cn.richie696.component.mcp.protocol.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpJsonRpcRequest} 紧凑构造器将 {@code null} params 归一为
 * 不可变空 Map；外部对入参的修改不影响请求；{@link #notification()} 仅在 id 为 null 时为真。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpJsonRpcRequest 线格式请求归一化")
class McpJsonRpcRequestTest {

    @Test
    @DisplayName("null params 在构造期归一为不可变空 Map")
    void nullParamsNormalizeToEmpty() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 1, "tools/list", null);

        assertThat(request.params()).isEmpty();
    }

    @Test
    @DisplayName("params 拷贝为不可变视图，外部修改入参不影响请求")
    void paramsAreImmutableSnapshot() {
        Map<String, Object> mutable = new HashMap<>();
        mutable.put("a", 1);

        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 1, "tools/list", mutable);
        mutable.put("b", 2);

        assertThat(request.params()).containsOnlyKeys("a");
        assertThatThrownBy(() -> request.params().put("c", 3))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("id 为 null → notification() = true；非 null → false")
    void notificationReflectsIdPresence() {
        McpJsonRpcRequest notification = new McpJsonRpcRequest("2.0", null, "events/ping", Map.of());
        McpJsonRpcRequest call = new McpJsonRpcRequest("2.0", 1, "tools/list", Map.of());

        assertThat(notification.notification()).isTrue();
        assertThat(call.notification()).isFalse();
    }

    @Test
    @DisplayName("params 中允许保留 JSON null 字段（Jackson 反序列化产物）")
    void jsonNullValuesArePreserved() {
        Map<String, Object> params = new HashMap<>();
        params.put("optional", null);
        params.put("present", 42);

        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", "r-1", "tools/call", params);

        assertThat(request.params()).containsEntry("optional", null).containsEntry("present", 42);
    }
}
