package cn.richie696.component.mcp.transport.stdio;

import cn.richie696.component.mcp.protocol.McpProtocolException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

/**
 * MCP modern STDIO newline-delimited JSON 编解码器。
 * <p>该类只处理 framing，不解释 JSON-RPC 业务语义。</p>
 */
public final class McpStdioFrameCodec implements McpStdioCodec {
    public static final int DEFAULT_MAX_FRAME_BYTES = 1024 * 1024;

    private final int maxFrameBytes;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public McpStdioFrameCodec() {
        this(DEFAULT_MAX_FRAME_BYTES);
    }

    public McpStdioFrameCodec(int maxFrameBytes) {
        if (maxFrameBytes <= 0) {
            throw new IllegalArgumentException("maxFrameBytes must be positive");
        }
        this.maxFrameBytes = maxFrameBytes;
    }

    public String encode(Map<String, Object> message) {
        if (message == null || message.isEmpty()) {
            throw invalid("STDIO message must be a non-empty object");
        }
        try {
            String json = jsonMapper.writeValueAsString(message);
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
