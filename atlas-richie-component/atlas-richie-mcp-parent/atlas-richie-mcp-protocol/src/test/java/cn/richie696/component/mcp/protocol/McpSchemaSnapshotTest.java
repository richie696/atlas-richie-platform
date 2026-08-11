package cn.richie696.component.mcp.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpSchemaSnapshot} 在加载时强制校验 source commit 与 SHA-256：只有官方
 * 固定的协议版本（{@code 2026-07-28}）才能加载快照，载荷必须包含 {@code DiscoverResult} /
 * {@code InputRequiredResult} 等关键模式；未固定的版本必须立即抛错，从而把"被中间人篡改
 * 的 schema"挡在 dispatcher 之前。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpSchemaSnapshot Schema 快照加载与校验")
class McpSchemaSnapshotTest {

    @Test
    @DisplayName("加载官方固定 schema：校验 schemaDialect / sourceCommit / SHA-256 摘要")
    void loadsPinnedOfficialSchemaAndVerifiesChecksum() {
        McpSchemaSnapshot snapshot = McpSchemaSnapshot.load(McpProtocolVersions.V_2026_07_28);
        String schema = new String(snapshot.bytes(), StandardCharsets.UTF_8);

        assertThat(snapshot.schemaDialect()).isEqualTo("https://json-schema.org/draft/2020-12/schema");
        assertThat(snapshot.sourceCommit()).isEqualTo("271ecc9accafdd9b83a3c869fa67c22953b2af80");
        assertThat(snapshot.protocolVersion()).isEqualTo(McpProtocolVersions.V_2026_07_28);
        assertThat(snapshot.source()).isNotBlank();
        assertThat(snapshot.sha256()).isEqualTo(
                "ef70b61f99b6d2e5e3b46863822eab08dff6a45bedc7a08914e0e5b133f40203");
        assertThat(snapshot.bytes()).hasSize(181_474);
        assertThat(schema)
                .contains("\"DiscoverResult\"")
                .contains("\"io.modelcontextprotocol/protocolVersion\"")
                .contains("\"InputRequiredResult\"")
                .contains("\"requestState\"");
    }

    @Test
    @DisplayName("bytes() 返回防御性拷贝，外部修改不影响内部")
    void bytesIsDefensiveCopy() {
        McpSchemaSnapshot snapshot = McpSchemaSnapshot.load(McpProtocolVersions.V_2026_07_28);
        byte[] copy = snapshot.bytes();
        copy[0] = (byte) 0xFF;

        assertThat(snapshot.bytes()[0]).isNotEqualTo((byte) 0xFF);
    }

    @Test
    @DisplayName("未固定的版本必须立即抛 IllegalArgumentException")
    void refusesVersionsWithoutAPinnedSnapshot() {
        assertThatThrownBy(() -> McpSchemaSnapshot.load(McpProtocolVersions.V_2025_11_25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(McpProtocolVersions.V_2025_11_25);
        assertThatThrownBy(() -> McpSchemaSnapshot.load("2099-01-01"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null version 必须抛 NPE（业务必填）")
    void rejectsNullVersion() {
        assertThatThrownBy(() -> McpSchemaSnapshot.load(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("protocolVersion");
    }
}
