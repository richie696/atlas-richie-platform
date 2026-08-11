package cn.richie696.component.mcp.transport.stdio;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 legacy {@link McpLegacyContentLengthCodec} 的 opt-in 互通能力：与
 * {@link McpStdioTransport} 组合时，能正确按 {@code Content-Length} 头拆分
 * framing 并解码 JSON，确保在需要与历史进程打通时，端到端链路仍然可用。
 *
 * @author richie696
 * @since 2026-08-11
 */
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
