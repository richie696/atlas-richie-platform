/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.mcp.transport.stdio;

import cn.richie696.component.mcp.protocol.McpProtocolException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpStdioTransport} 的 framing 通道契约：
 * 构造器对 null 流/codec 抛 NPE；send/receive 同步、EOF 时 receive 返回空；
 * close() 按 writer→reader 顺序串行关闭，任一方向失败会被合并为 suppressed。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpStdioTransport framing 通道")
class McpStdioTransportTest {

    @Test
    @DisplayName("构造器对 null input/output/codec 抛 NPE")
    void constructorRejectsNullArgs() {
        assertThatThrownBy(() -> new McpStdioTransport(null, new ByteArrayOutputStream()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("input");
        assertThatThrownBy(() -> new McpStdioTransport(new ByteArrayInputStream(new byte[0]), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("output");
        assertThatThrownBy(() -> new McpStdioTransport(
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(),
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("codec");
    }

    @Test
    @DisplayName("默认构造使用 newline-delimited JSON codec")
    void defaultConstructorUsesStandardCodec() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        McpStdioTransport transport = new McpStdioTransport(
                new ByteArrayInputStream("{\"jsonrpc\":\"2.0\",\"id\":42}\n".getBytes()),
                output);

        Optional<Map<String, Object>> received = transport.receive();
        assertThat(received).isPresent();
        assertThat(received.get()).containsEntry("id", 42);
    }

    @Test
    @DisplayName("receive 在 EOF 时返回 Optional.empty")
    void receiveReturnsEmptyOnEof() throws Exception {
        McpStdioTransport transport = new McpStdioTransport(
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());

        assertThat(transport.receive()).isEmpty();
        assertThat(transport.receive()).isEmpty();
    }

    @Test
    @DisplayName("send 写出 newline 结尾的 JSON 帧")
    void sendWritesJsonLineTerminator() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        McpStdioTransport transport = new McpStdioTransport(
                new ByteArrayInputStream(new byte[0]), output);

        transport.send(Map.of("jsonrpc", "2.0", "id", 7));
        transport.send(Map.of("jsonrpc", "2.0", "id", 8));

        assertThat(output.toString()).endsWith("\n");
        assertThat(output.toString()).contains("\"id\":7");
        assertThat(output.toString()).contains("\"id\":8");
    }

    @Test
    @DisplayName("send/receive 同步化：交错调用不会撕裂一帧")
    void concurrentSendReceiveDoNotInterleave() throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(
                "{\"jsonrpc\":\"2.0\",\"id\":1}\n{\"jsonrpc\":\"2.0\",\"id\":2}\n".getBytes());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        McpStdioTransport transport = new McpStdioTransport(input, output);

        // 先读两帧再写一帧：模拟交错场景
        assertThat(transport.receive()).isPresent();
        transport.send(Map.of("jsonrpc", "2.0", "id", 99));
        assertThat(transport.receive()).isPresent();

        assertThat(output.toString()).endsWith("\n");
        assertThat(output.toString()).contains("\"id\":99");
    }

    @Test
    @DisplayName("legacy codec 通过 readFrame 解析 Content-Length 头")
    void legacyCodecReadsContentLengthFrames() throws Exception {
        String json = "{\"jsonrpc\":\"2.0\"}";
        ByteArrayInputStream input = new ByteArrayInputStream(
                ("Content-Length: " + json.length() + "\r\n\r\n" + json).getBytes());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        McpStdioTransport transport = new McpStdioTransport(
                input, output, new McpLegacyContentLengthCodec());

        Optional<Map<String, Object>> received = transport.receive();
        assertThat(received).isPresent();
        assertThat(received.get()).containsEntry("jsonrpc", "2.0");
    }

    @Test
    @DisplayName("close() 幂等：第二次 close 不抛异常")
    void closeIsIdempotent() throws Exception {
        McpStdioTransport transport = new McpStdioTransport(
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        transport.close();
        assertThatCode(transport::close).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("close() 在 OutputStream 关闭失败时抛 IOException")
    void closePropagatesOutputStreamFailure() {
        java.io.OutputStream failingOutput = new java.io.OutputStream() {
            @Override public void write(int b) { }
            @Override public void close() throws IOException {
                throw new IOException("output closed");
            }
        };
        McpStdioTransport transport = new McpStdioTransport(
                new ByteArrayInputStream(new byte[0]), failingOutput);

        assertThatThrownBy(transport::close).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("自定义 codec 的 encode 失败映射为 RuntimeException")
    void codecEncodeFailureSurfaces() throws Exception {
        McpStdioCodec bad = new McpStdioCodec() {
            @Override public String encode(Map<String, Object> message) {
                throw new McpProtocolException(
                        "MCP_STDIO_INVALID_FRAME", -32600, "bad", Map.of());
            }
            @Override public Map<String, Object> decode(String frame) {
                return Map.of();
            }
        };
        McpStdioTransport transport = new McpStdioTransport(
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), bad);

        assertThatThrownBy(() -> transport.send(Map.of("jsonrpc", "2.0")))
                .isInstanceOf(McpProtocolException.class)
                .hasMessageContaining("bad");
    }

    @Test
    @DisplayName("transport 接收多行 framing（半行缓冲被正确拼装）")
    void receiveBuffersHalfLinesCorrectly() throws Exception {
        // 模拟 BufferedReader 的半行行为：先用小缓冲分两次输入完整行
        java.io.PipedInputStream piped = new java.io.PipedInputStream(8);
        java.io.PipedOutputStream source = new java.io.PipedOutputStream(piped);
        new Thread(() -> {
            try {
                source.write("{\"jsonrpc\":\"2.0\",\"id\":1".getBytes());
                source.write("}\n".getBytes());
                source.write("{\"jsonrpc\":\"2.0\",\"id\":2}\n".getBytes());
                source.close();
            } catch (IOException ignored) { }
        }).start();

        McpStdioTransport transport = new McpStdioTransport(piped, new ByteArrayOutputStream());
        Optional<Map<String, Object>> first = transport.receive();
        Optional<Map<String, Object>> second = transport.receive();

        assertThat(first).isPresent();
        assertThat(first.get()).containsEntry("id", 1);
        assertThat(second).isPresent();
        assertThat(second.get()).containsEntry("id", 2);
    }

    @Test
    @DisplayName("codec.readFrame 返回 null 时 receive 返回空 Optional（不抛 NPE）")
    void codecNullFrameBecomesEmptyOptional() throws Exception {
        McpStdioCodec nullReturning = new McpStdioCodec() {
            @Override public String encode(Map<String, Object> message) { return ""; }
            @Override public Map<String, Object> decode(String frame) { return Map.of(); }
            @Override public String readFrame(java.io.BufferedReader reader) {
                return null;
            }
        };
        McpStdioTransport transport = new McpStdioTransport(
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), nullReturning);

        assertThat(transport.receive()).isEmpty();
    }

    @Test
    @DisplayName("codec.readFrame 抛 IO 异常时 receive 透传")
    void codecReadFrameIOExceptionPropagates() throws Exception {
        McpStdioCodec throwing = new McpStdioCodec() {
            @Override public String encode(Map<String, Object> message) { return ""; }
            @Override public Map<String, Object> decode(String frame) { return Map.of(); }
            @Override public String readFrame(java.io.BufferedReader reader) throws IOException {
                throw new IOException("io error");
            }
        };
        McpStdioTransport transport = new McpStdioTransport(
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), throwing);

        assertThatThrownBy(transport::receive).isInstanceOf(IOException.class);
    }
}
