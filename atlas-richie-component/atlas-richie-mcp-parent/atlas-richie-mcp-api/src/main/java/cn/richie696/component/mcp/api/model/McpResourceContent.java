package cn.richie696.component.mcp.api.model;

import java.util.List;
import java.util.Map;

/**
 * Resource 读取后的内容载体。
 *
 * <p>对应 MCP 协议 {@code resources/read} 的返回：一份 Resource 可能由多个"内容片段"组成
 * （例如一份多页 PDF 的每个分页），因此使用 {@code List<Map<String, Object>>} 而非单条
 * 内容。设计为协议无关结构——具体内容形态（文本/二进制/base64）由协议适配层根据
 * {@code mimeType} 字段解释。</p>
 *
 * @param contents 内容片段列表
 * @author richie696
 * @since 2026-08-11
 */
public record McpResourceContent(List<Map<String, Object>> contents) {
    /**
     * 紧凑构造器：不可变拷贝。
     *
     * @param contents 内容片段列表
     */
    public McpResourceContent {
        contents = contents == null ? List.of() : List.copyOf(contents);
    }
}
