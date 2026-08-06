package cn.richie696.component.mcp.transport.stdio;

import cn.richie696.component.mcp.protocol.McpProtocolException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Opt-in legacy LSP Content-Length framing; never used by the standard codec. */
public final class McpLegacyContentLengthCodec implements McpStdioCodec {
    private final int maxFrameBytes;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public McpLegacyContentLengthCodec() {
        this(McpStdioFrameCodec.DEFAULT_MAX_FRAME_BYTES);
    }

    public McpLegacyContentLengthCodec(int maxFrameBytes) {
        if (maxFrameBytes <= 0) throw new IllegalArgumentException("maxFrameBytes must be positive");
        this.maxFrameBytes = maxFrameBytes;
    }

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
