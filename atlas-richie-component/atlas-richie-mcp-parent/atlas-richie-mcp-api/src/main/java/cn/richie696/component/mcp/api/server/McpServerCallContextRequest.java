package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpCancellationToken;
import cn.richie696.component.mcp.api.McpProgressReporter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Framework-neutral request data used to create the business call context.
 *
 * <p>协议层（HTTP/SSE）把一次请求的所有"未解释"信息（header、请求 ID、协议版本、deadline 等）
 * 装入该 record 后交给 {@link McpCallContextFactory}。注意：该类型是"未做业务解释"的纯协议
 * 透传，业务信息（tenant/subject/scopes）由工厂负责从 header 中解析得到，避免 {@code
 * atlas-richie-mcp-server} 依赖具体鉴权实现。</p>
 *
 * @param requestId 请求 ID
 * @param protocolVersion 协议版本
 * @param headers HTTP 头（多值）
 * @param attributes 协议层附加属性
 * @param defaultDeadline 默认截止时间
 * @param cancellationToken 取消令牌，缺省回落为 {@link McpCancellationToken#NONE}
 * @param progressReporter 进度回报，缺省回落为 {@link McpProgressReporter#NOOP}
 * @author richie696
 * @since 2026-08-11
 */
public record McpServerCallContextRequest(
        String requestId,
        String protocolVersion,
        Map<String, List<String>> headers,
        Map<String, Object> attributes,
        Instant defaultDeadline,
        McpCancellationToken cancellationToken,
        McpProgressReporter progressReporter) {

    /**
     * 紧凑构造器：对 Header 多值列表与属性 Map 做防御性不可变拷贝，并对取消/进度端口做缺省回落。
     *
     * <p>使用 {@link LinkedHashMap} 保持 header 顺序，便于在日志/审计场景回放请求时
     * 维持与接收顺序一致的可读性。</p>
     *
     * @param requestId 请求 ID
     * @param protocolVersion 协议版本
     * @param headers HTTP 头
     * @param attributes 附加属性
     * @param defaultDeadline 默认截止时间
     * @param cancellationToken 取消令牌
     * @param progressReporter 进度回报
     */
    public McpServerCallContextRequest {
        Map<String, List<String>> headerCopy = new LinkedHashMap<>();
        if (headers != null) {
            headers.forEach((name, values) -> headerCopy.put(
                    name, values == null ? List.of()
                            : Collections.unmodifiableList(new ArrayList<>(values))));
        }
        headers = Collections.unmodifiableMap(headerCopy);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        cancellationToken = cancellationToken == null
                ? McpCancellationToken.NONE : cancellationToken;
        progressReporter = progressReporter == null
                ? McpProgressReporter.NOOP : progressReporter;
    }
}
