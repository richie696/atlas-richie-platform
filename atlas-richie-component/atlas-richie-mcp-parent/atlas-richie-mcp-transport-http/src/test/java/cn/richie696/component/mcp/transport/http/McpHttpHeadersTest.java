package cn.richie696.component.mcp.transport.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpHttpHeaders} 维护的标准 HTTP 头名称常量一一对应，避免字符串字面量散落
 * 各处导致拼写错误：包括 Streamable HTTP 双边 {@code Content-Type} / {@code Accept}
 * 协议镜像头 {@code MCP-Protocol-Version} / {@code Mcp-Method} / {@code Mcp-Name}
 * 以及形参镜像前缀 {@code Mcp-Param-}。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpHttpHeaders 头名称常量")
class McpHttpHeadersTest {

    @Test
    @DisplayName("七个标准头名称与设计一致")
    void constantsHaveExpectedValues() {
        assertThat(McpHttpHeaders.ACCEPT).isEqualTo("Accept");
        assertThat(McpHttpHeaders.CONTENT_TYPE).isEqualTo("Content-Type");
        assertThat(McpHttpHeaders.ORIGIN).isEqualTo("Origin");
        assertThat(McpHttpHeaders.PROTOCOL_VERSION).isEqualTo("MCP-Protocol-Version");
        assertThat(McpHttpHeaders.METHOD).isEqualTo("Mcp-Method");
        assertThat(McpHttpHeaders.NAME).isEqualTo("Mcp-Name");
        assertThat(McpHttpHeaders.PARAMETER_PREFIX).isEqualTo("Mcp-Param-");
    }
}
