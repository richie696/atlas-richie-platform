package cn.richie696.component.mcp.protocol.discovery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpCacheScope} 在两个枚举值（{@code PUBLIC} / {@code PRIVATE}）与
 * 线格式字面量之间的双向映射契约；非法字面量必须拒绝。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpCacheScope 缓存作用域枚举")
class McpCacheScopeTest {

    @Test
    @DisplayName("枚举同时包含 PUBLIC 与 PRIVATE")
    void enumContainsBothScopes() {
        assertThat(McpCacheScope.values())
                .containsExactlyInAnyOrder(McpCacheScope.PUBLIC, McpCacheScope.PRIVATE);
    }

    @Test
    @DisplayName("wireValue() 严格返回小写字面量")
    void wireValueIsLowercase() {
        assertThat(McpCacheScope.PUBLIC.wireValue()).isEqualTo("public");
        assertThat(McpCacheScope.PRIVATE.wireValue()).isEqualTo("private");
    }

    @Test
    @DisplayName("fromWireValue 双向解析：合法字面量映射回枚举")
    void fromWireValueRoundtrips() {
        assertThat(McpCacheScope.fromWireValue("public")).isEqualTo(McpCacheScope.PUBLIC);
        assertThat(McpCacheScope.fromWireValue("private")).isEqualTo(McpCacheScope.PRIVATE);
    }

    @Test
    @DisplayName("fromWireValue 拒绝非法字面量（含大小写错误的 PUBLIC）")
    void fromWireValueRejectsUnknown() {
        assertThatThrownBy(() -> McpCacheScope.fromWireValue("PUBLIC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported MCP cache scope");
        assertThatThrownBy(() -> McpCacheScope.fromWireValue(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> McpCacheScope.fromWireValue("none"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
