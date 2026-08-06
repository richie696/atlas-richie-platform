package cn.richie696.component.mcp.schema;

import java.util.Map;

/**
 * 不暴露具体 JSON Schema 引擎类型的中台校验端口。
 */
public interface McpJsonSchemaValidator {
    McpCompiledSchema compile(Map<String, Object> schema);
}
