package cn.richie696.component.mcp.protocol.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * 验证 {@link McpJsonRpcError} 紧凑构造器强制 {@code message} 非空，
 * 三个字段（{@code code} / {@code message} / {@code data}）保持 record 风格直读。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpJsonRpcError 错误对象契约")
class McpJsonRpcErrorTest {

    @Test
    @DisplayName("正常三字段构造可读回")
    void exposesAllFields() {
        McpJsonRpcError error = new McpJsonRpcError(-32600, "invalid request", Map.of("hint", "id"));

        assertThat(error.code()).isEqualTo(-32600);
        assertThat(error.message()).isEqualTo("invalid request");
        assertThat((Map<String, Object>) error.data()).containsEntry("hint", "id");
    }

    @Test
    @DisplayName("data 字段允许为 null（线格式上 data 可省略）")
    void dataMayBeNull() {
        McpJsonRpcError error = new McpJsonRpcError(-32601, "method not found", null);

        assertThat(error.data()).isNull();
    }

    @Test
    @DisplayName("null message 必须抛 NPE（避免下游日志出现空描述）")
    void rejectsNullMessage() {
        assertThatNullPointerException()
                .isThrownBy(() -> new McpJsonRpcError(-32600, null, null))
                .withMessageContaining("message");
    }

    @Test
    @DisplayName("空白 message 不被构造期拒绝（仅 null 必填校验）")
    void blankMessageIsNotRejectedByCompactConstructor() {
        org.assertj.core.api.Assertions.assertThatNoException()
                .isThrownBy(() -> new McpJsonRpcError(-32600, "", null));
        org.assertj.core.api.Assertions.assertThatNoException()
                .isThrownBy(() -> new McpJsonRpcError(-32600, "   ", null));
    }
}
