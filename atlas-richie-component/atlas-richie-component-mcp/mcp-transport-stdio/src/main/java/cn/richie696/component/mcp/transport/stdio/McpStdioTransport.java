package cn.richie696.component.mcp.transport.stdio;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 stdin/stdout 的同步 STDIO framing 通道。
 */
public final class McpStdioTransport implements Closeable {
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final McpStdioCodec codec;

    public McpStdioTransport(InputStream input, OutputStream output) {
        this(input, output, new McpStdioFrameCodec());
    }

    public McpStdioTransport(
            InputStream input,
            OutputStream output,
            McpStdioCodec codec) {
        this.reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(input, "input"), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(
                Objects.requireNonNull(output, "output"), StandardCharsets.UTF_8));
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public synchronized void send(Map<String, Object> message) throws IOException {
        writer.write(codec.encode(message));
        writer.flush();
    }

    public synchronized Optional<Map<String, Object>> receive() throws IOException {
        String line = codec.readFrame(reader);
        return line == null ? Optional.empty() : Optional.of(codec.decode(line));
    }

    @Override
    public synchronized void close() throws IOException {
        IOException failure = null;
        try {
            writer.close();
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            reader.close();
        } catch (IOException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
