package cn.richie696.component.mcp.protocol.mrtr;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpRequestStateCodecTest {
    private final McpRequestStateCodec codec = new McpRequestStateCodec(
            "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));

    @Test
    void protectsAndVerifiesOpaqueState() {
        String token = codec.protect("opaque", "principal-a", "tools/call",
                Instant.now().plusSeconds(30));

        assertThat(codec.verify(token, "principal-a", "tools/call").payload()).isEqualTo("opaque");
    }

    @Test
    void bindsStateToPrincipalAndMethod() {
        String token = codec.protect("opaque", "principal-a", "tools/call", Instant.now().plusSeconds(30));

        assertThatThrownBy(() -> codec.verify(token, "principal-b", "tools/call"))
                .hasMessageContaining("Invalid or expired");
    }
}
