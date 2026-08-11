package cn.richie696.component.mcp.protocol.compatibility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpProbeEvent} 工厂方法与紧凑构造器的归一化契约：
 * null 列表归一为不可变空列表；外部修改入参不影响事件内容；工厂方法总是构造合法事件。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpProbeEvent 探测事件归一化")
class McpProbeEventTest {

    @Test
    @DisplayName("discoverResult 工厂方法保留声明版本并归一为不可变副本")
    void discoverResultPreservesAdvertisedVersions() {
        List<String> versions = new ArrayList<>();
        versions.add("2026-07-28");
        versions.add("2025-11-25");
        McpProbeEvent event = McpProbeEvent.discoverResult(versions);

        versions.add("2099-01-01");
        assertThat(event.advertisedVersions())
                .containsExactly("2026-07-28", "2025-11-25");
        assertThatThrownBy(() -> event.advertisedVersions().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(event.type()).isEqualTo(McpProbeEvent.Type.DISCOVER_RESULT);
        assertThat(event.httpStatus()).isNull();
        assertThat(event.jsonRpcErrorCode()).isNull();
    }

    @Test
    @DisplayName("null 声明版本归一为不可变空列表")
    void nullAdvertisedVersionsNormalizeToEmpty() {
        McpProbeEvent event = McpProbeEvent.discoverResult(null);

        assertThat(event.advertisedVersions()).isEmpty();
    }

    @Test
    @DisplayName("modernSuccess 工厂方法将 httpStatus 原样写入")
    void modernSuccessCarriesHttpStatus() {
        McpProbeEvent event = McpProbeEvent.modernSuccess(200);

        assertThat(event.type()).isEqualTo(McpProbeEvent.Type.MODERN_SUCCESS);
        assertThat(event.httpStatus()).isEqualTo(200);
        assertThat(event.jsonRpcErrorCode()).isNull();
        assertThat(event.advertisedVersions()).isEmpty();
    }

    @Test
    @DisplayName("jsonRpcError 工厂方法同时支持 http status 与 advertised versions")
    void jsonRpcErrorCarriesAllFields() {
        McpProbeEvent event = McpProbeEvent.jsonRpcError(
                500, -32600, List.of("2026-07-28"));

        assertThat(event.type()).isEqualTo(McpProbeEvent.Type.JSON_RPC_ERROR);
        assertThat(event.httpStatus()).isEqualTo(500);
        assertThat(event.jsonRpcErrorCode()).isEqualTo(-32600);
        assertThat(event.advertisedVersions()).containsExactly("2026-07-28");
    }

    @Test
    @DisplayName("jsonRpcError 允许 httpStatus 为 null")
    void jsonRpcErrorAcceptsNullHttpStatus() {
        McpProbeEvent event = McpProbeEvent.jsonRpcError(null, -32601, List.of());

        assertThat(event.httpStatus()).isNull();
    }

    @Test
    @DisplayName("transportError 工厂方法原样写入 http status")
    void transportErrorCarriesHttpStatus() {
        McpProbeEvent event = McpProbeEvent.transportError(503);

        assertThat(event.type()).isEqualTo(McpProbeEvent.Type.TRANSPORT_ERROR);
        assertThat(event.httpStatus()).isEqualTo(503);
        assertThat(event.jsonRpcErrorCode()).isNull();
    }

    @Test
    @DisplayName("timeout 工厂方法固定字段")
    void timeoutHasFixedShape() {
        McpProbeEvent event = McpProbeEvent.timeout();

        assertThat(event.type()).isEqualTo(McpProbeEvent.Type.TIMEOUT);
        assertThat(event.httpStatus()).isNull();
        assertThat(event.jsonRpcErrorCode()).isNull();
        assertThat(event.advertisedVersions()).isEmpty();
    }

    @Test
    @DisplayName("Type 枚举包含 5 种事件类型")
    void typeEnumHasFiveValues() {
        assertThat(McpProbeEvent.Type.values())
                .containsExactlyInAnyOrder(
                        McpProbeEvent.Type.DISCOVER_RESULT,
                        McpProbeEvent.Type.MODERN_SUCCESS,
                        McpProbeEvent.Type.JSON_RPC_ERROR,
                        McpProbeEvent.Type.TRANSPORT_ERROR,
                        McpProbeEvent.Type.TIMEOUT);
    }
}
