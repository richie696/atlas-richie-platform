package cn.richie696.component.mcp.transport.stdio;

import cn.richie696.component.mcp.protocol.McpProtocolException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 兼容用 legacy LSP {@code Content-Length} framing 编解码器。
 *
 * <p>本类实现早期 LSP / 早期 MCP 协议使用的 {@code Content-Length} 头 + 双 CRLF 分隔的 framing
 * 协议，<b>仅</b>用于与历史进程（无法升级到 newline-delimited JSON 协议的旧服务端 / 客户端）
 * 互通。设计上承担三个关键职责：</p>
 * <ul>
 *   <li><b>显式 opt-in</b>：默认 codec 仍是 {@link McpStdioFrameCodec}；本 codec 必须由调用方
 *       通过 {@link McpStdioProcess#start(java.util.List, java.time.Duration, McpStdioCodec)}
 *       显式注入，永远不会被 {@code McpStdioTransport} 默认构造使用。</li>
 *   <li><b>字节级读取</b>：通过 {@link #readFrame(BufferedReader)} 按字符读取并校验 UTF-8 字节
 *       数与声明 Content-Length 一致，避免对端伪造长度导致分配漏洞。</li>
 *   <li><b>隔离错误码</b>：所有失败统一映射为 {@code MCP_STDIO_LEGACY_INVALID_FRAME}，便于
 *       监控侧区分"链路 framing"与"协议兼容"两类问题。</li>
 * </ul>
 *
 * <p>之所以需要这个兼容 codec：社区中仍存在部分以 LSP 风格发放的 MCP 服务（如某些 IDE 插件），
 * 仅支持 {@code Content-Length} framing；提供显式开关让上层按需启用，而不是污染默认路径。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpLegacyContentLengthCodec implements McpStdioCodec {
    private final int maxFrameBytes;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /**
     * 使用 {@link McpStdioFrameCodec#DEFAULT_MAX_FRAME_BYTES} 1 MiB 上限构造 codec。
     */
    public McpLegacyContentLengthCodec() {
        this(McpStdioFrameCodec.DEFAULT_MAX_FRAME_BYTES);
    }

    /**
     * 使用自定义帧上限构造 codec。
     *
     * @param maxFrameBytes 单帧字节上限（UTF-8 编码后），必须为正整数
     * @throws IllegalArgumentException 当 {@code maxFrameBytes} 非正时抛出
     */
    public McpLegacyContentLengthCodec(int maxFrameBytes) {
        if (maxFrameBytes <= 0) throw new IllegalArgumentException("maxFrameBytes must be positive");
        this.maxFrameBytes = maxFrameBytes;
    }

    /**
     * 将 JSON 对象序列化为 {@code Content-Length} 头 + JSON 体的 LSP 风格帧。
     *
     * @param message 待编码的 JSON 对象映射
     * @return 完整的 {@code Content-Length} framing 字符串
     * @throws McpProtocolException 当序列化失败或帧超长时抛出
     */
    @Override
    public String encode(Map<String, Object> message) {
        try {
            String json = jsonMapper.writeValueAsString(message);
            int length = json.getBytes(StandardCharsets.UTF_8).length;
            if (length > maxFrameBytes) throw invalid("legacy frame exceeds configured maximum");
            return "Content-Length: " + length + "\r\n\r\n" + json;
        } catch (JacksonException exception) {
            throw invalid("legacy message is not JSON encodable");
        }
    }

    /**
     * 将已剥除 framing 头部的 JSON 字符串解码为不可变 Map。
     *
     * @param frame 纯 JSON 字符串（不含 {@code Content-Length} 头）
     * @return 不可变字符串键的 Map
     * @throws McpProtocolException 当 JSON 解析失败或 payload 非对象时抛出
     */
    @Override
    public Map<String, Object> decode(String frame) {
        try {
            Map<?, ?> raw = jsonMapper.readValue(frame, Map.class);
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, value) -> {
                if (key instanceof String text) result.put(text, value);
            });
            return java.util.Collections.unmodifiableMap(result);
        } catch (JacksonException | ClassCastException exception) {
            throw invalid("legacy frame is not a JSON object");
        }
    }

    /**
     * 按 {@code Content-Length} header 从 reader 中读取恰好指定字节数的 JSON payload。
     *
     * <p>读取流程：解析首行 header → 校验空行分隔 → 按字符（UTF-8 字节计数）读取直至累计字节
     * 等于声明长度。任一字段缺失或不一致都会转换为 {@link McpProtocolException}；EOF 遇
     * 帧未读完也会抛错以避免无限阻塞。</p>
     *
     * @param reader 由 {@link McpStdioTransport} 提供的缓冲字符输入流
     * @return 完整的 JSON payload 字符串；若读到 header 之前 EOF 则返回 {@code null}
     * @throws IOException 底层 IO 失败时抛出
     * @throws McpProtocolException 当 header 缺失、长度非法、separator 缺失或字节数不一致时抛出
     */
    @Override
    public String readFrame(BufferedReader reader) throws IOException {
        String header = reader.readLine();
        if (header == null) return null;
        if (!header.startsWith("Content-Length:")) throw invalid("missing Content-Length header");
        int length;
        try {
            length = Integer.parseInt(header.substring("Content-Length:".length()).trim());
        } catch (NumberFormatException exception) {
            throw invalid("invalid Content-Length header");
        }
        if (length < 0 || length > maxFrameBytes) throw invalid("invalid Content-Length value");
        String separator = reader.readLine();
        if (separator == null || !separator.isEmpty()) throw invalid("missing Content-Length separator");
        StringBuilder frame = new StringBuilder(length);
        int bytes = 0;
        while (bytes < length) {
            int character = reader.read();
            if (character < 0) throw invalid("unexpected EOF in legacy frame");
            char value = (char) character;
            // 中文说明：必须按"UTF-8 字节数"递增，而不是简单地按字符数。
            // 非 ASCII 字符（如中文）在 UTF-8 下可能占 2~4 字节，若仅用 chars 累加，
            // 一旦对端声明的 Content-Length 与物理字节数不一致，就有可能让 payload
            // 截断或越界读取，因此此处逐字符核对字节数。
            int valueBytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8).length;
            bytes += valueBytes;
            if (bytes > length) throw invalid("Content-Length does not match UTF-8 payload");
            frame.append(value);
        }
        return frame.toString();
    }

    private McpProtocolException invalid(String message) {
        return new McpProtocolException("MCP_STDIO_LEGACY_INVALID_FRAME", -32600, message, Map.of());
    }
}
