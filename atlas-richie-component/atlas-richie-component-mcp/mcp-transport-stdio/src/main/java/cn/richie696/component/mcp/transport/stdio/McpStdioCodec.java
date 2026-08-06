package cn.richie696.component.mcp.transport.stdio;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

public interface McpStdioCodec {
    String encode(Map<String, Object> message);

    Map<String, Object> decode(String frame);

    default String readFrame(BufferedReader reader) throws IOException {
        return reader.readLine();
    }
}
