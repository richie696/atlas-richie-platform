package cn.richie696.component.mcp.transport.stdio;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

/**
 * MCP STDIO 通道的 framing 协议契约。
 *
 * <p>本接口抽象了"如何把 JSON 对象切分成可流式传输的帧"，以及"如何从流中重新拼出 JSON 对象"。
 * {@link McpStdioTransport} 仅依赖本接口与 {@link BufferedReader}，因此业务侧可以通过注入
 * 不同实现灵活切换 framing 协议：标准实现 {@link McpStdioFrameCodec}（newline-delimited JSON）
 * 是默认选择；legacy 实现 {@link McpLegacyContentLengthCodec}（{@code Content-Length}
 * 头格式）用于与历史 LSP / 早期 MCP 进程互通。</p>
 *
 * <p>设计上承担两个关键职责：</p>
 * <ul>
 *   <li><b>纯 framing</b>：不解释 JSON-RPC 业务字段（method / id / params 等），任何语义
 *       校验都由上层 dispatcher 负责。</li>
 *   <li><b>可插拔流读取</b>：通过 {@link #readFrame(BufferedReader)} 让需要按字节/header
 *       解析的 framing（如 legacy Content-Length）落地到同一接口，调用方零修改。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
 */
public interface McpStdioCodec {
    /**
     * 将 JSON 对象序列化为可流式传输的 framing 字符串。
     *
     * @param message 待编码的 JSON 对象映射
     * @return 合法的 framing 字符串（具体格式由实现定义）
     * @throws RuntimeException 当 JSON 序列化失败或帧违反本地约束（超长、含换行等）时抛出
     */
    String encode(Map<String, Object> message);

    /**
     * 将 framing 字符串解码回 JSON 对象映射。
     *
     * @param frame 已剥离底层字节边界的 JSON 字符串（具体形态由 {@link #readFrame} 决定）
     * @return 不可变字符串键的 Map
     * @throws RuntimeException 当 JSON 解析失败或 payload 不符合协议要求时抛出
     */
    Map<String, Object> decode(String frame);

    /**
     * 默认按行读取一帧。
     *
     * <p>大多数 newline-delimited JSON 框架（例如 {@link McpStdioFrameCodec}）只需
     * {@link BufferedReader#readLine()} 即可完成 framing；只有像 legacy
     * {@code Content-Length} 这类需要解析 header 的协议才需要覆盖此方法。</p>
     *
     * @param reader 由 {@link McpStdioTransport} 提供的缓冲字符输入流
     * @return 完整的一帧字符串；流 EOF 时返回 {@code null}
     * @throws IOException 底层 IO 失败时抛出
     */
    default String readFrame(BufferedReader reader) throws IOException {
        return reader.readLine();
    }
}
