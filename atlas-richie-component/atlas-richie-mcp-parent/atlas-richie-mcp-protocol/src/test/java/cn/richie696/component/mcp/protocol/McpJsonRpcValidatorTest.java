package cn.richie696.component.mcp.protocol;

import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcError;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpJsonRpcValidator} 对 JSON-RPC 2.0 顶层结构与 id 类型规则的严格校验：
 * 字符串 / 整数 / {@code null}（notification）id 合法；浮点 / 布尔 id 必须拒绝；
 * 非 {@code 2.0} 版本号直接判定为 {@code -32600} invalid request；params 内允许
 * 保留 JSON {@code null}；{@link McpJsonRpcResponse} 必须且只能包含 {@code result} 或
 * {@code error} 之一，否则构造期抛 {@link IllegalArgumentException}。
 *
 * @author richie696
 * @since 2026-08-11
 */
class McpJsonRpcValidatorTest {
    @Test
    void acceptsStringIntegerAndNotificationIds() {
        assertThatCode(() -> McpJsonRpcValidator.validate(request("req-1"))).doesNotThrowAnyException();
        assertThatCode(() -> McpJsonRpcValidator.validate(request(42L))).doesNotThrowAnyException();
        assertThatCode(() -> McpJsonRpcValidator.validate(request(null))).doesNotThrowAnyException();
    }

    @Test
    void rejectsFractionalAndBooleanIds() {
        assertThatThrownBy(() -> McpJsonRpcValidator.validate(request(new BigDecimal("1.5"))))
                .isInstanceOf(McpProtocolException.class);
        assertThatThrownBy(() -> McpJsonRpcValidator.validate(request(true)))
                .isInstanceOf(McpProtocolException.class);
    }

    @Test
    void rejectsWrongJsonRpcVersion() {
        McpJsonRpcRequest request = new McpJsonRpcRequest("1.0", 1, "tools/list", Map.of());

        assertThatThrownBy(() -> McpJsonRpcValidator.validate(request))
                .isInstanceOfSatisfying(McpProtocolException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.jsonRpcCode())
                                .isEqualTo(-32600));
    }

    @Test
    void preservesJsonNullInParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("optionalArgument", null);

        McpJsonRpcRequest request = new McpJsonRpcRequest("2.0", 1, "tools/call", params);

        assertThatCode(() -> McpJsonRpcValidator.validate(request)).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(request.params())
                .containsEntry("optionalArgument", null);
    }

    @Test
    void responseContainsExactlyOneOfResultOrError() {
        assertThatCode(() -> new McpJsonRpcResponse("2.0", 1, Map.of(), null))
                .doesNotThrowAnyException();
        assertThatCode(() -> new McpJsonRpcResponse(
                "2.0", 1, null, new McpJsonRpcError(-32600, "invalid", Map.of())))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new McpJsonRpcResponse(
                "2.0", 1, Map.of(), new McpJsonRpcError(-32600, "invalid", Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new McpJsonRpcResponse("2.0", 1, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private McpJsonRpcRequest request(Object id) {
        return new McpJsonRpcRequest("2.0", id, "tools/list", Map.of());
    }
}
