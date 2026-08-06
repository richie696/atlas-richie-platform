package cn.richie696.component.mcp.transport.stdio;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpLegacyContentLengthCodecTest {
    @Test
    void supportsOptInContentLengthFraming() throws Exception {
        McpLegacyContentLengthCodec codec = new McpLegacyContentLengthCodec();
        String frame = codec.encode(Map.of("jsonrpc", "2.0", "method", "ping"));
        McpStdioTransport transport = new McpStdioTransport(
                new ByteArrayInputStream(frame.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                new ByteArrayOutputStream(), codec);

        Map<String, Object> received = transport.receive().orElseThrow();
        assertThat(received.get("method")).isEqualTo("ping");
    }
}
