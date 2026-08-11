package cn.richie696.component.mcp.protocol.dialect;

import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpDialectRegistry} 的协议版本索引契约：
 * 默认注册 modern + legacy 两个方言；重复版本号被立即拒绝；缺失版本号按
 * {@code -32022} 抛出 {@link McpProtocolException}；{@link #dialects()} 提供不可变视图。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpDialectRegistry 协议版本注册表")
class McpDialectRegistryTest {

    @Test
    @DisplayName("默认构造器同时注册 modern + legacy 两个方言")
    void defaultConstructorRegistersBothDialects() {
        McpDialectRegistry registry = new McpDialectRegistry();

        assertThat(registry.dialects())
                .extracting(McpProtocolDialect::version)
                .containsExactlyInAnyOrder(
                        McpProtocolVersions.V_2026_07_28,
                        McpProtocolVersions.V_2025_11_25);
    }

    @Test
    @DisplayName("require() 命中注册版本时返回对应方言")
    void requireReturnsRegisteredDialect() {
        McpDialectRegistry registry = new McpDialectRegistry();

        assertThat(registry.require(McpProtocolVersions.V_2026_07_28).era())
                .isEqualTo(cn.richie696.component.mcp.protocol.McpProtocolEra.STATELESS_2026);
        assertThat(registry.require(McpProtocolVersions.V_2025_11_25).era())
                .isEqualTo(cn.richie696.component.mcp.protocol.McpProtocolEra.SESSION_2025);
    }

    @Test
    @DisplayName("require() 未注册版本抛 -32022 McpProtocolException")
    void requireUnknownVersionThrows() {
        McpDialectRegistry registry = new McpDialectRegistry();

        assertThatThrownBy(() -> registry.require("2099-01-01"))
                .isInstanceOfSatisfying(McpProtocolException.class, exception -> {
                    assertThat(exception.jsonRpcCode()).isEqualTo(-32022);
                    assertThat(exception.errorCode()).isEqualTo("MCP_UNSUPPORTED_PROTOCOL_VERSION");
                });
    }

    @Test
    @DisplayName("同一版本号被多次注册直接抛 IllegalArgumentException")
    void duplicateVersionIsRejected() {
        McpProtocolDialect a = new Mcp20260728Dialect();
        McpProtocolDialect b = new Mcp20260728Dialect();
        Collection<McpProtocolDialect> dialects = List.of(a, b);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new McpDialectRegistry(dialects))
                .withMessageContaining("Duplicate MCP dialect")
                .withMessageContaining(McpProtocolVersions.V_2026_07_28);
    }

    @Test
    @DisplayName("自定义注册表：仅注册一个版本也能正常工作")
    void customRegistryWithSingleDialect() {
        McpDialectRegistry registry = new McpDialectRegistry(
                List.of(new Mcp20260728Dialect()));

        assertThat(registry.dialects())
                .extracting(McpProtocolDialect::version)
                .containsExactly(McpProtocolVersions.V_2026_07_28);
        assertThatThrownBy(() -> registry.require(McpProtocolVersions.V_2025_11_25))
                .isInstanceOf(McpProtocolException.class);
    }
}
