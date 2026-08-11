package cn.richie696.component.mcp.transport.stdio;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpStdioFrameCodec} 的 newline-delimited JSON framing 与
 * {@link McpStdioTransport} 的 flush / EOF 行为：编码输出以 {@code \n} 结尾，
 * 解码可还原原始对象；transport 能正确处理 half-line 缓冲与 EOF；超出配置的
 * 帧上限必须被拒绝。该测试为 STDIO 通道的最小可运行契约。
 *
 * @author richie696
 * @since 2026-08-11
 */
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
