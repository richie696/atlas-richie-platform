package cn.richie696.component.mcp.protocol.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON 解码后的最小线格式；传输模块负责将 JSON 映射到该类型。
 *
 * <p>为什么是"最小"：本 record 只承载 JSON-RPC 2.0 与 MCP 共有的四个字段
 * （{@code jsonrpc / id / method / params}），不引入任何协议时代相关字段——
 * 那些字段由 {@link McpNormalizedRequest} 负责表达，dialect 层负责两者之间的转换。
 * 这样传输层只需关心 JSON 反序列化产物即可，不必区分协议时代。</p>
 *
 * @param jsonrpc 协议版本字面量（应为 {@code "2.0"}，由 {@link cn.richie696.component.mcp.protocol.McpJsonRpcValidator} 校验）
 * @param id      请求 id；通知型为 {@code null}
 * @param method  方法名
 * @param params  参数 Map
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpJsonRpcRequest(String jsonrpc, Object id, String method, Map<String, Object> params) {
    /**
     * 紧凑构造器：{@code null} 参数归一为 {@link Map#of()} 并防止外部修改。
     */
    public McpJsonRpcRequest {
        params = params == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    /**
     * 判断是否为通知型请求（无 id）。
     *
     * @return {@code true} 表示通知型
     */
    public boolean notification() {
        return id == null;
    }
}

