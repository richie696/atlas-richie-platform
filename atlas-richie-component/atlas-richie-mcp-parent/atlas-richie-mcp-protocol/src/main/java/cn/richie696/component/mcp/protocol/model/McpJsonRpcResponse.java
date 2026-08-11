package cn.richie696.component.mcp.protocol.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON-RPC 2.0 响应线格式（{@code jsonrpc / id / result / error}）。
 *
 * <p>关键约束：JSON-RPC 2.0 规定 {@code result} 与 {@code error} 二者必有其一、不可兼得。
 * 本 record 的紧凑构造器在编译期就强制该不变量——任何试图同时或同时缺失的构造都会抛
 * {@link IllegalArgumentException}，避免带病响应流到下游。</p>
 *
 * <p>{@code result} 字段在 {@code null} 时归一为 {@link Map#of()}，调用方无需判空；
 * 非 {@code null} 时会被拷贝为不可变 Map。</p>
 *
 * @param jsonrpc 协议版本字面量，本组件固定为 {@code "2.0"}
 * @param id      对端请求的 id（通知型请求响应为 {@code null}）
 * @param result  成功时的负载，二选一必填
 * @param error   失败时的错误对象，二选一必填
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpJsonRpcResponse(String jsonrpc, Object id, Map<String, Object> result, McpJsonRpcError error) {
    /**
     * 紧凑构造器：校验 result/error 互斥并归一化 result。
     *
     * @throws IllegalArgumentException 当 result 与 error 同时存在或同时缺失时
     */
    public McpJsonRpcResponse {
        boolean resultPresent = result != null;
        if (resultPresent == (error != null)) {
            // 同时为 null 或同时非 null 都违反 JSON-RPC 2.0 互斥约束
            throw new IllegalArgumentException("JSON-RPC response must contain exactly one of result or error");
        }
        result = result == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }
}
