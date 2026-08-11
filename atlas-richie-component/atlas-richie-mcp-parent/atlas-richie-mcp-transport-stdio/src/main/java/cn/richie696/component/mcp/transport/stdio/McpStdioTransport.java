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
 *
 * <p>本类把 {@link InputStream} / {@link OutputStream} 包装成线程安全的 {@code send/receive} 通道，
 * 通过 {@link McpStdioCodec} 抽象 framing 协议。设计上承担三个关键职责：</p>
 * <ul>
 *   <li><b>线程安全</b>：所有公共方法均 {@code synchronized}，对应"单写单读"的子进程 IPC 场景；
 *       任何读写交错都由本类通过对象锁串行化，避免 JSON 帧被切断。</li>
 *   <li><b>字符集锁定</b>：读写器固定为 UTF-8，避免跨平台时子进程预期与 JVM 假设不一致导致的乱码。</li>
 *   <li><b>优雅关停</b>：{@link #close()} 采用先 writer 后 reader 的顺序并合并异常，确保任一
 *       方向关停失败时另一个方向仍被关闭，避免单边泄漏。</li>
 * </ul>
 *
 * <p>之所以需要这个适配器：{@link Process#getInputStream()} / {@link Process#getOutputStream()}
 * 仅是裸字节流，直接使用极易写出"半行 JSON"或"未刷新的写入"等不可恢复的协议错误；本类把
 * framing + 缓冲 + 同步 + 收尾串成一个原子化通道。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpStdioTransport implements Closeable {
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final McpStdioCodec codec;

    /**
     * 使用标准 newline-delimited JSON codec 构造通道。
     *
     * @param input 上游输入流（通常是子进程 stdout），不可为空
     * @param output 下游输出流（通常是子进程 stdin），不可为空
     */
    public McpStdioTransport(InputStream input, OutputStream output) {
        this(input, output, new McpStdioFrameCodec());
    }

    /**
     * 使用指定的 framing 编解码器构造通道。
     *
     * @param input 上游输入流（通常是子进程 stdout），不可为空
     * @param output 下游输出流（通常是子进程 stdin），不可为空
     * @param codec framing 编解码器；不可为空
     */
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

    /**
     * 同步发送一条 MCP 消息。
     *
     * <p>调用方线程会持有通道锁直到 JSON 帧完整写入并 flush，避免与并发 {@code send/receive}
     * 互相穿插。</p>
     *
     * @param message 待发送的 JSON 对象（必须为非空映射）
     * @throws IOException 写底层流失败时抛出
     */
    public synchronized void send(Map<String, Object> message) throws IOException {
        writer.write(codec.encode(message));
        writer.flush();
    }

    /**
     * 同步读取一条 MCP 消息。
     *
     * <p>返回 {@link Optional#empty()} 表示上游已 EOF（子进程关闭了 stdout），调用方应据此
     * 退出接收循环并触发 {@link McpStdioProcess#close()} 链路回收。</p>
     *
     * @return 已解码的 JSON 对象；若读到 EOF 则返回空
     * @throws IOException 读底层流失败时抛出
     */
    public synchronized Optional<Map<String, Object>> receive() throws IOException {
        String line = codec.readFrame(reader);
        return line == null ? Optional.empty() : Optional.of(codec.decode(line));
    }

    /**
     * 关闭双向通道。
     *
     * <p>先关闭写入端再关闭读取端，并按"先发生者抛原始异常、后者通过
     * {@link Throwable#addSuppressed(Throwable)} 附加"的方式合并异常，确保调用方既能
     * 感知到第一个失败原因，又不会丢失次要失败信息。</p>
     *
     * @throws IOException 任一方向的关闭失败都会抛出（首个为主，后续通过 suppressed 合并）
     */
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
