/**
 * 经典 STDIO 传输内核：以子进程 stdin/stdout 作为 MCP 消息通道，提供进程启动、frame 编解码、
 * 阻塞式发送/接收以及优雅关停等一站式能力。
 *
 * <p>本包内的类按职责划分为以下几组：
 * <ul>
 *   <li><b>进程生命周期</b>：{@link cn.richie696.component.mcp.transport.stdio.McpStdioProcess}
 *       —— 封装 {@link Process} 启动、销毁与超时强杀，并通过 {@link java.io.Closeable} 与
 *       try-with-resources 协作，确保子进程在调用方异常或正常返回时都能被回收。</li>
 *   <li><b>同步通道</b>：{@link cn.richie696.component.mcp.transport.stdio.McpStdioTransport}
 *       —— 基于 {@link java.io.BufferedReader} / {@link java.io.BufferedWriter} 的线程安全
 *       同步收发通道，所有方法 {@code synchronized}，适配单写单读的低频进程内 IPC 场景。</li>
 *   <li><b>frame 编解码契约</b>：{@link cn.richie696.component.mcp.transport.stdio.McpStdioCodec}
 *       —— 抽象 framing 协议；默认实现为 newline-delimited JSON，提供可选的 legacy
 *       {@code Content-Length} 兼容实现供旧版 MCP 服务端/客户端互通使用。</li>
 *   <li><b>现代 framing</b>：{@link cn.richie696.component.mcp.transport.stdio.McpStdioFrameCodec}
 *       —— 标准 newline-delimited JSON 编解码器，包含字节上限校验、空白/换行拒绝、明确
 *       非空对象约束，并将异常统一映射为 {@code MCP_STDIO_INVALID_FRAME} 错误码。</li>
 *   <li><b>遗留 framing</b>：{@link cn.richie696.component.mcp.transport.stdio.McpLegacyContentLengthCodec}
 *       —— 仿 LSP/早期 MCP 的 {@code Content-Length} 头 framing，仅在需要与历史进程桥接时
 *       通过显式注入使用，不作为默认 codec。</li>
 * </ul>
 *
 * <p>之所以把这些类放在同一个包：它们共同实现"以本地子进程为载体的 MCP 传输"这一边界。
 * 进程对象、读写通道以及 framing 协议三者紧耦合（{@code McpStdioProcess} 默认构造
 * {@code McpStdioTransport} 与 {@code McpStdioFrameCodec}，{@code McpStdioTransport} 又依赖
 * 可插拔的 {@code McpStdioCodec}），拆到子包反而会暴露内部实现细节并造成循环依赖。
 * 包内严禁引入任何 Web 框架依赖，确保 STDIO 传输可在任意 JVM 进程内独立复用。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
package cn.richie696.component.mcp.transport.stdio;
