package cn.richie696.component.mcp.transport.stdio;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * MCP STDIO 子进程生命周期适配器。
 *
 * <p>本类把 {@link Process} 的启动、运行、销毁三段生命周期封装成可被 try-with-resources 托管
 * 的资源对象，并在此基础上绑定一个 {@link McpStdioTransport} 同步通道，使调用方能像使用普通
 * 集合一样使用"以子进程 stdin/stdout 作为载体"的 MCP 通信。设计上承担三个关键职责：</p>
 * <ul>
 *   <li><b>进程隔离</b>：默认把子进程 stderr 透传给当前 JVM（{@code INHERIT}），避免子进程
 *       日志被吞到死管道中；同时保证 stdout 严格保留给 framing 协议独占。</li>
 *   <li><b>优雅关停</b>：{@link #close()} 先关闭双向通道，再按
 *       {@code shutdownTimeout → destroyForcibly} 顺序兜底，避免半关管道导致子进程挂死。</li>
 *   <li><b>可插拔 codec</b>：默认使用 {@link McpStdioFrameCodec}（newline-delimited JSON），
 *       高级用户可注入 {@link McpStdioCodec}（例如 legacy {@code Content-Length}
 *       实现）以兼容历史进程。</li>
 * </ul>
 *
 * <p>之所以需要这个适配器：{@link Process} 的 API 过于底层，且 JVM 不会在父进程退出时回收孤儿
 *  MCP 子进程；本类把"启动 + 信道 + 关闭"这一观察窗口内的不变量集中维护，避免散落在业务侧
 *  造成僵尸进程 / 死锁的偶发问题。</p>
 *
 * @author richie696
 * @since 2026-08-11
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

    /**
     * 使用默认 5 秒关停超时与标准 newline-delimited JSON codec 启动子进程。
     *
     * @param command 启动命令及其参数（首元素为可执行文件路径），不可为空或包含空字符串
     * @return 已就绪的 {@link McpStdioProcess}
     * @throws IOException 当 {@link ProcessBuilder#start()} 失败时抛出
     */
    public static McpStdioProcess start(List<String> command) throws IOException {
        return start(command, Duration.ofSeconds(5));
    }

    /**
     * 启动子进程并指定关停超时。
     *
     * @param command 启动命令及其参数（首元素为可执行文件路径），不可为空或包含空字符串
     * @param shutdownTimeout 关停超时，必须为正时长
     * @return 已就绪的 {@link McpStdioProcess}
     * @throws IOException 当 {@link ProcessBuilder#start()} 失败时抛出
     */
    public static McpStdioProcess start(List<String> command, Duration shutdownTimeout) throws IOException {
        return start(command, shutdownTimeout, new McpStdioFrameCodec());
    }

    /**
     * 启动子进程，允许同时指定关停超时与 frame 编解码器。
     *
     * <p>启动前会校验 {@code command} 不为空、不含空参，以及 {@code shutdownTimeout} 为正时长；
     * 子进程 stderr 默认直连到当前 JVM（{@code INHERIT}），stdout/stdin 严格保留给 framing 协议。</p>
     *
     * @param command 启动命令及其参数（首元素为可执行文件路径），不可为空或包含空字符串
     * @param shutdownTimeout 关停超时，必须为正时长
     * @param codec framing 编解码器；不可为空
     * @return 已就绪的 {@link McpStdioProcess}
     * @throws IOException 当 {@link ProcessBuilder#start()} 失败时抛出
     */
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

    /**
     * 获取与本进程关联的同步 MCP STDIO 通道。
     *
     * @return 共享同一生命周期的 {@link McpStdioTransport} 实例
     */
    public McpStdioTransport transport() {
        return transport;
    }

    /**
     * 暴露底层 {@link Process} 对象，便于高级用户获取 PID、退出码或附加自定义监控。
     *
     * @return 底层 Java 进程对象
     */
    public Process process() {
        return process;
    }

    /**
     * 关闭 MCP 通道并优雅回收子进程。
     *
     * <p>关闭顺序：先关闭 transport（让进行中的 JSON 帧写完），再按
     * {@code shutdownTimeout} 等待子进程自然退出；若超时仍未退出，则切换为
     * {@link Process#destroyForcibly()} 兜底。等待过程中若线程被中断，会恢复中断标志
     * 并直接强杀。</p>
     *
     * @throws IOException 当关闭 transport 过程中发生 IO 异常时抛出
     */
    @Override
    public void close() throws IOException {
        try {
            transport.close();
        } finally {
            if (process.isAlive()) {
                process.destroy();
                try {
                    if (!process.waitFor(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                        // 中文说明：自然退避超时后必须立即降级为 destroyForcibly。
                        // 否则某些平台（如 Windows）下，仅 destroy() 不会真正回收子进程，
                        // 造成 close() 长期挂起或进程泄漏。
                        process.destroyForcibly();
                        process.waitFor(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS);
                    }
                } catch (InterruptedException exception) {
                    // 中文说明：等待期间被中断时，必须恢复中断标志以保留上游协作语义，
                    // 同时放弃软退避直接强杀，避免再次 waitFor 仍被中断导致资源泄漏。
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                }
            }
        }
    }
}
