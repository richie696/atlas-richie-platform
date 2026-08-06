package cn.richie696.component.mcp.transport.stdio;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpStdioFrameCodecTest {
    @Test
    void encodesAndDecodesOneJsonObjectPerLine() throws Exception {
        McpStdioFrameCodec codec = new McpStdioFrameCodec();
        String frame = codec.encode(Map.of("jsonrpc", "2.0", "method", "ping"));

        assertThat(frame).endsWith("\n");
        assertThat(codec.decode(frame.strip())).containsEntry("method", "ping");
    }

    @Test
    void transportFlushesFramesAndReadsEof() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        McpStdioTransport transport = new McpStdioTransport(
                new ByteArrayInputStream("{\"jsonrpc\":\"2.0\",\"id\":1}\n".getBytes()), output);

        Map<String, Object> received = transport.receive().orElseThrow();
        assertThat(received.get("id")).isEqualTo(1);
        assertThat(transport.receive()).isEmpty();
        transport.send(Map.of("jsonrpc", "2.0", "id", 1, "result", Map.of()));
        assertThat(output.toString()).endsWith("\n");
    }

    @Test
    void rejectsOversizedFrame() {
        McpStdioFrameCodec codec = new McpStdioFrameCodec(10);

        assertThatThrownBy(() -> codec.encode(Map.of("message", "too long")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("exceeds");
    }
}
