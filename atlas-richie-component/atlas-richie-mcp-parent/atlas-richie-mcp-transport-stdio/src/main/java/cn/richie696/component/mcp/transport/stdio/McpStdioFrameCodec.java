package cn.richie696.component.mcp.transport.stdio;

import cn.richie696.component.mcp.protocol.McpProtocolException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

/**
 * MCP modern STDIO newline-delimited JSON 编解码器。
 *
 * <p>本类是 {@link McpStdioCodec} 的标准实现：每个 JSON 帧占一行（{@code \n} 分隔），与 MCP
 * 2025-06-18 协议中 STDIO transport 的 framing 规则一致。该类只处理 framing，不解释
 * JSON-RPC 业务语义；任何解码出来的消息都会以不可变 {@link Map} 形式交给上层 dispatcher。
 * 关键设计点：</p>
 * <ul>
 *   <li><b>单行约束</b>：编码 / 解码两端均显式拒绝在 JSON 内夹带 {@code \n} 或 {@code \r}，避免
 *       对端 {@link BufferedReader#readLine()} 误判帧边界。</li>
 *   <li><b>字节上限</b>：默认 1 MiB，可由构造函数覆盖；任何超限帧都会被映射为
 *       {@code MCP_STDIO_INVALID_FRAME} 错误码，防止恶意 / 异常进程恶灌导致内存爆炸。</li>
 *   <li><b>非空对象约束</b>：空映射或仅含非字符串键的映射会被拒绝，确保 dispatcher 拿到的
 *       都是合法 JSON-RPC 消息载体。</li>
 *   <li><b>不可变输出</b>：返回的 map 用 {@link Collections#unmodifiableMap} 包裹，防止下游
 *       意外篡改 framing 后的数据。</li>
 * </ul>
 *
 * <p>之所以需要这个编解码器：newline-delimited JSON 是 MCP STDIO 唯一必需的 framing 协议；
 * 把它独立成可注入的 codec，业务侧既可以单独测试 framing 边界，又能在后续切换为
 * {@link McpLegacyContentLengthCodec} 时无需修改 {@link McpStdioTransport}。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpStdioFrameCodec implements McpStdioCodec {
    public static final int DEFAULT_MAX_FRAME_BYTES = 1024 * 1024;

    private final int maxFrameBytes;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /**
     * 使用 1 MiB 帧上限构造 codec。
     */
    public McpStdioFrameCodec() {
        this(DEFAULT_MAX_FRAME_BYTES);
    }

    /**
     * 使用自定义帧上限构造 codec。
     *
     * @param maxFrameBytes 单帧字节上限（UTF-8 编码后），必须为正整数
     * @throws IllegalArgumentException 当 {@code maxFrameBytes} 非正时抛出
     */
    public McpStdioFrameCodec(int maxFrameBytes) {
        if (maxFrameBytes <= 0) {
            throw new IllegalArgumentException("maxFrameBytes must be positive");
        }
        this.maxFrameBytes = maxFrameBytes;
    }

    /**
     * 将 JSON 对象序列化为单行 framing 字符串。
     *
     * <p>序列化后追加 {@code '\n'} 作为帧分隔符；任何含换行字符或超长帧都会转换为
     * {@link McpProtocolException}，避免对端把一行误判为多行。</p>
     *
     * @param message 待编码的 JSON 对象映射，不可为空或空映射
     * @return 以 {@code '\n'} 结尾的帧字符串
     * @throws McpProtocolException 当消息为空、含换行、超长或 Jackson 序列化失败时抛出
     */
    public String encode(Map<String, Object> message) {
        if (message == null || message.isEmpty()) {
            throw invalid("STDIO message must be a non-empty object");
        }
        try {
            String json = jsonMapper.writeValueAsString(message);
            // 中文说明：序列化结果必须严格保持单行；任何换行符都会让对端 readLine()
            // 误把一帧拆成多帧，进而导致 JSON-RPC 解析错乱。
            if (json.indexOf('\n') >= 0 || json.indexOf('\r') >= 0) {
                throw invalid("STDIO message must be a single line");
            }
            if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxFrameBytes) {
                throw invalid("STDIO frame exceeds configured maximum");
            }
            return json + '\n';
        } catch (JacksonException exception) {
            throw invalid("STDIO message is not JSON encodable", exception);
        }
    }

    /**
     * 将单行 JSON 字符串解码为不可变 Map。
     *
     * <p>解码过程会拒绝空行、含换行符、超长或非对象类型的 JSON；所有失败统一映射为
     * {@code MCP_STDIO_INVALID_FRAME}（错误码 {@code -32600}），与 JSON-RPC invalid request
     * 语义对齐。</p>
     *
     * @param line 单行 JSON 字符串（不含 {@code \n} / {@code \r}），不可为空空白
     * @return 不可变字符串键的 Map
     * @throws McpProtocolException 当帧非法、含换行、超长或解析失败时抛出
     */
    public Map<String, Object> decode(String line) {
        if (line == null || line.isBlank()) {
            throw invalid("STDIO frame must not be blank");
        }
        if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
            throw invalid("STDIO frame must not contain a newline");
        }
        if (line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxFrameBytes) {
            throw invalid("STDIO frame exceeds configured maximum");
        }
        try {
            Map<?, ?> raw = jsonMapper.readValue(line, Map.class);
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, value) -> {
                if (!(key instanceof String text)) {
                    // 中文说明：JSON 规范允许非字符串键，但 MCP 协议依赖字符串字段名
                    // （method/id/error.code 等）。若放任非字符串键透传，
                    // dispatcher 将无法匹配 method 等核心字段，从而导致协议解析失败。
                    throw invalid("STDIO message contains a non-string object key");
                }
                result.put(text, value);
            });
            if (result.isEmpty()) {
                throw invalid("STDIO message must be a non-empty object");
            }
            return Collections.unmodifiableMap(result);
        } catch (McpProtocolException exception) {
            throw exception;
        } catch (JacksonException | ClassCastException exception) {
            throw invalid("STDIO frame is not a valid JSON object", exception);
        }
    }

    private McpProtocolException invalid(String message) {
        return invalid(message, null);
    }

    private McpProtocolException invalid(String message, Throwable cause) {
        return new McpProtocolException("MCP_STDIO_INVALID_FRAME", -32600, message, Map.of(), cause);
    }
}
