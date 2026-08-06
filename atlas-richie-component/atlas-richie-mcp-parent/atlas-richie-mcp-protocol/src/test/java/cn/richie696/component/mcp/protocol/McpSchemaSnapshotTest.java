package cn.richie696.component.mcp.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpSchemaSnapshotTest {
    @Test
    void loadsPinnedOfficialSchemaAndVerifiesChecksum() {
        McpSchemaSnapshot snapshot = McpSchemaSnapshot.load(McpProtocolVersions.V_2026_07_28);
        String schema = new String(snapshot.bytes(), StandardCharsets.UTF_8);

        assertThat(snapshot.schemaDialect()).isEqualTo("https://json-schema.org/draft/2020-12/schema");
        assertThat(snapshot.sourceCommit()).isEqualTo("271ecc9accafdd9b83a3c869fa67c22953b2af80");
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
    void refusesVersionsWithoutAPinnedSnapshot() {
        assertThatThrownBy(() -> McpSchemaSnapshot.load(McpProtocolVersions.V_2025_11_25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(McpProtocolVersions.V_2025_11_25);
    }
}
