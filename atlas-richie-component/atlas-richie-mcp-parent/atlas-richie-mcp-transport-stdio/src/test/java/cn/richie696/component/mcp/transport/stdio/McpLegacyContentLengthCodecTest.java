package cn.richie696.component.mcp.transport.stdio;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void validatesLegacyHeadersUtf8LengthAndJsonPayload() throws Exception {
        McpLegacyContentLengthCodec codec = new McpLegacyContentLengthCodec(32);
        String json = "{\"消息\":\"值\"}";
        String frame = "Content-Length: " + json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                + "\r\n\r\n" + json;
        assertThat(codec.readFrame(new BufferedReader(new StringReader(frame)))).isEqualTo(json);
        assertThat(codec.decode(json)).containsEntry("消息", "值");
        assertThat(codec.readFrame(new BufferedReader(new StringReader("")))).isNull();
        assertThatThrownBy(() -> new McpLegacyContentLengthCodec(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode("[]")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> codec.decode("not-json")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsMalformedLegacyFramesAndOversizedMessages() {
        McpLegacyContentLengthCodec codec = new McpLegacyContentLengthCodec(8);
        assertThatThrownBy(() -> codec.encode(Map.of("message", "too long")))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> codec.readFrame(reader("X-Length: 1\r\n\r\n{}")))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> codec.readFrame(reader("Content-Length: nope\r\n\r\n{}")))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> codec.readFrame(reader("Content-Length: 99\r\n\r\n{}")))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> codec.readFrame(reader("Content-Length: 2\r\nwrong\n{}")))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> codec.readFrame(reader("Content-Length: 2\r\n\r\n{")))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> codec.readFrame(reader("Content-Length: 1\r\n\r\n中")))
                .isInstanceOf(RuntimeException.class);
    }

    private BufferedReader reader(String value) {
        return new BufferedReader(new StringReader(value));
    }
}
