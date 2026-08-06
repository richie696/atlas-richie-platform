package cn.richie696.component.mcp.transport.stdio;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * MCP STDIO 子进程生命周期适配器。
 */
public final class McpStdioProcess implements Closeable {
    private final Process process;
    private final McpStdioTransport transport;
    private final Duration shutdownTimeout;

    private McpStdioProcess(Process process, Duration shutdownTimeout, McpStdioCodec codec) {
        this.process = process;
        this.shutdownTimeout = shutdownTimeout;
        this.transport = new McpStdioTransport(process.getInputStream(), process.getOutputStream(),
                Objects.requireNonNull(codec, "codec"));
    }

    public static McpStdioProcess start(List<String> command) throws IOException {
        return start(command, Duration.ofSeconds(5));
    }

    public static McpStdioProcess start(List<String> command, Duration shutdownTimeout) throws IOException {
        return start(command, shutdownTimeout, new McpStdioFrameCodec());
    }

    public static McpStdioProcess start(
            List<String> command,
            Duration shutdownTimeout,
            McpStdioCodec codec) throws IOException {
        if (command == null || command.isEmpty() || command.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("STDIO command must not be empty");
        }
        if (shutdownTimeout == null || shutdownTimeout.isNegative() || shutdownTimeout.isZero()) {
            throw new IllegalArgumentException("shutdownTimeout must be positive");
        }
        Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        return new McpStdioProcess(process, shutdownTimeout, codec);
    }

    public McpStdioTransport transport() {
        return transport;
    }

    public Process process() {
        return process;
    }

    @Override
    public void close() throws IOException {
        try {
            transport.close();
        } finally {
            if (process.isAlive()) {
                process.destroy();
                try {
                    if (!process.waitFor(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                        process.destroyForcibly();
                        process.waitFor(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                }
            }
        }
    }
}
