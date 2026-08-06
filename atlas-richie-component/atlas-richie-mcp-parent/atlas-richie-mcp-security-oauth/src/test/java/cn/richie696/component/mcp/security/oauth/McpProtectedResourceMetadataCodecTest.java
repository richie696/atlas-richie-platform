package cn.richie696.component.mcp.security.oauth;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpProtectedResourceMetadataCodecTest {
    @Test
    void encodesRfc9728FieldNames() {
        Map<String, Object> wire = McpProtectedResourceMetadataCodec.encode(
                new McpProtectedResourceMetadata(
                        URI.create("https://mcp.example/mcp"),
                        List.of(URI.create("https://idp.example")),
                        List.of("tools.read"),
                        Map.of("x-tenant", "required")));

        assertThat(wire).containsEntry("resource", "https://mcp.example/mcp")
                .containsEntry("authorization_servers", List.of("https://idp.example"))
                .containsEntry("scopes_supported", List.of("tools.read"))
                .containsEntry("x-tenant", "required");
    }
}
