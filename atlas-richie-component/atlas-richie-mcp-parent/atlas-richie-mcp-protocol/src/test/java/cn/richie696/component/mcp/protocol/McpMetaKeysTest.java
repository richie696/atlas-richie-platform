package cn.richie696.component.mcp.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpMetaKeys} 暴露的协议级 {@code _meta} 键是稳定契约：
 * 所有常量非空、互不重复（避免协议级名称冲突），命名空间归属符合 Javadoc 约定。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpMetaKeys 协议级 _meta 键常量")
class McpMetaKeysTest {

    @Test
    @DisplayName("所有协议命名空间键都带有 io.modelcontextprotocol/ 前缀")
    void namespacedKeysCarryProtocolPrefix() {
        assertThat(McpMetaKeys.PROTOCOL_VERSION)
                .startsWith("io.modelcontextprotocol/");
        assertThat(McpMetaKeys.CLIENT_INFO)
                .startsWith("io.modelcontextprotocol/");
        assertThat(McpMetaKeys.CLIENT_CAPABILITIES)
                .startsWith("io.modelcontextprotocol/");
        assertThat(McpMetaKeys.SERVER_INFO)
                .startsWith("io.modelcontextprotocol/");
        assertThat(McpMetaKeys.LOG_LEVEL)
                .startsWith("io.modelcontextprotocol/");
        assertThat(McpMetaKeys.SUBSCRIPTION_ID)
                .startsWith("io.modelcontextprotocol/");
    }

    @Test
    @DisplayName("PROGRESS_TOKEN 是 JSON-RPC 2.0 通用键，不带 MCP 命名空间")
    void progressTokenLivesOutsideMcpNamespace() {
        assertThat(McpMetaKeys.PROGRESS_TOKEN).isEqualTo("progressToken");
        assertThat(McpMetaKeys.PROGRESS_TOKEN)
                .doesNotStartWith("io.modelcontextprotocol/");
    }

    @Test
    @DisplayName("所有常量值非空、非空白且互不重复")
    void constantsAreUniqueAndNonBlank() {
        String[] all = {
                McpMetaKeys.PROTOCOL_VERSION,
                McpMetaKeys.CLIENT_INFO,
                McpMetaKeys.CLIENT_CAPABILITIES,
                McpMetaKeys.SERVER_INFO,
                McpMetaKeys.LOG_LEVEL,
                McpMetaKeys.SUBSCRIPTION_ID,
                McpMetaKeys.PROGRESS_TOKEN
        };

        for (String value : all) {
            assertThat(value).isNotNull();
            assertThat(value).isNotBlank();
        }
        Set<String> unique = new HashSet<>(Arrays.asList(all));
        assertThat(unique).hasSize(all.length);
    }
}
