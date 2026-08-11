package cn.richie696.component.mcp.protocol;

import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

/**
 * 在进入 Dialect 前执行与版本无关的 JSON-RPC 2.0 校验。
 *
 * <p>为什么需要这个闸口：MCP 协议基于 JSON-RPC 2.0，但 JSON-RPC 2.0 仅规定报文必须字段
 * （{@code jsonrpc / method}），对 {@code id} 的合法性留有较大弹性（字符串、整数、空值、
 * 小数等皆可）。本类把所有方言共用的最小校验集中在入口处执行：
 * <ul>
 *   <li>{@code jsonrpc} 必须严格为 {@code "2.0"}；</li>
 *   <li>{@code method} 非空；</li>
 *   <li>{@code id}（如果提供）必须是字符串或整数（含 {@code BigInteger}、{@code BigDecimal}）。</li>
 * </ul>
 * 业务级（参数、版本、能力）校验留给 {@code dialect} 处理。</p>
 *
 * <p>关键设计：
 * <ul>
 *   <li>{@code id} 的整数判定兼容 {@code BigDecimal.stripTrailingZeros().scale() <= 0}——
 *       即允许 {@code 1.0} 这种"形式是小数但数值上是整数"的形式，规避上游 JSON 库
 *       把整数渲染成小数的常见陷阱。</li>
 *   <li>校验失败抛 {@link McpProtocolException}，错误码 {@code -32600}（Invalid Request）。</li>
 * </ul>
 * </p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpJsonRpcValidator {
    private McpJsonRpcValidator() {
    }

    /**
     * 校验入参是否符合 JSON-RPC 2.0 的最低要求。
     *
     * @param request 待校验的 JSON-RPC 请求
     * @throws McpProtocolException 当报文字段缺失或 {@code id} 类型非法时
     */
    public static void validate(McpJsonRpcRequest request) {
        if (request == null) {
            throw invalidRequest("Request must not be null");
        }
        if (!"2.0".equals(request.jsonrpc())) {
            throw invalidRequest("jsonrpc must be exactly 2.0");
        }
        if (request.method() == null || request.method().isBlank()) {
            throw invalidRequest("method must not be blank");
        }
        if (request.id() != null && !validId(request.id())) {
            throw invalidRequest("id must be a string or an integer number");
        }
    }

    private static boolean validId(Object id) {
        if (id instanceof String) {
            return true;
        }
        if (id instanceof Byte || id instanceof Short || id instanceof Integer || id instanceof Long
                || id instanceof BigInteger) {
            return true;
        }
        if (id instanceof BigDecimal decimal) {
            // 容忍 "1.0" 这种"形式是小数但值是整数"的情形，规避上游 JSON 库的渲染策略差异
            return decimal.stripTrailingZeros().scale() <= 0;
        }
        return false;
    }

    private static McpProtocolException invalidRequest(String message) {
        return new McpProtocolException("MCP_INVALID_REQUEST", -32600, message, Map.of());
    }
}
