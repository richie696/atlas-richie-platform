package cn.richie696.component.mcp.protocol.dialect;

import cn.richie696.component.mcp.protocol.McpJsonRpcValidator;
import cn.richie696.component.mcp.protocol.McpProtocolEra;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.model.McpImplementationInfo;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;
import cn.richie696.component.mcp.protocol.model.McpNormalizedRequest;
import cn.richie696.component.mcp.protocol.model.McpNormalizedResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 2025-11-25 会话/initialize 协议时代适配器。
 *
 * <p>关键差异：此时代下，握手信息（{@code protocolVersion / clientInfo / capabilities}）
 * 出现在 {@code initialize} 请求的顶层参数中，而非 {@code _meta}。同时本时代不识别
 * 显式的 {@code resultType} 字段——若响应里出现非 {@code complete} 类型，直接视为协议
 * 违规。</p>
 *
 * 归一化策略：
 * <ul>
 *   <li>{@code initialize} 请求：必传 {@code protocolVersion / clientInfo / capabilities}，
 *       三者从 {@code arguments} 中剥离以免业务侧重复处理。</li>
 *   <li>非 {@code initialize} 请求：版本从 {@code transportProtocolVersion} 推断，
 *       对端信息视为 {@code null}。</li>
 *   <li>响应：仅允许 {@code complete}（缺省即视为 complete）。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class Mcp20251125Dialect implements McpProtocolDialect {
    /**
     * 返回该方言对应的协议版本号。
     *
     * @return 恒为 {@link McpProtocolVersions#V_2025_11_25}
     */
    @Override
    public String version() {
        return McpProtocolVersions.V_2025_11_25;
    }

    /**
     * 返回该方言所属的协议时代。
     *
     * @return 恒为 {@link McpProtocolEra#SESSION_2025}
     */
    @Override
    public McpProtocolEra era() {
        return McpProtocolEra.SESSION_2025;
    }

    /**
     * 把 JSON-RPC 线格式请求归一化为内部模型。
     *
     * @param request                   线格式请求
     * @param transportProtocolVersion  传输层协商出的协议版本（仅非 {@code initialize} 请求使用）
     * @return 归一化后的内部请求
     * @throws cn.richie696.component.mcp.protocol.McpProtocolException 当协议版本不匹配或必填字段缺失时
     */
    @Override
    public McpNormalizedRequest normalizeRequest(McpJsonRpcRequest request, String transportProtocolVersion) {
        McpJsonRpcValidator.validate(request);
        Map<String, Object> params = request.params();
        boolean initialize = "initialize".equals(request.method());
        String requestedVersion = initialize
                ? DialectSupport.string(params, "protocolVersion", true)
                : transportProtocolVersion;
        if (requestedVersion == null) {
            requestedVersion = version();
        }
        if (!version().equals(requestedVersion)) {
            throw new cn.richie696.component.mcp.protocol.McpProtocolException(
                    "MCP_PROTOCOL_VERSION_MISMATCH",
                    -32022,
                    "Legacy request protocol version does not match selected dialect",
                    Map.of("expected", version(), "actual", requestedVersion));
        }

        McpImplementationInfo peer = initialize
                ? DialectSupport.implementation(params, "clientInfo", true)
                : null;
        Map<String, Object> capabilities = initialize
                ? DialectSupport.object(params, "capabilities", true)
                : Map.of();
        Map<String, Object> arguments = new LinkedHashMap<>(params);
        if (initialize) {
            // 把握手元数据从业务参数中剥离，业务侧只需关心真正的方法参数
            arguments.remove("protocolVersion");
            arguments.remove("clientInfo");
            arguments.remove("capabilities");
        }
        return new McpNormalizedRequest(
                request.id(),
                request.method(),
                arguments,
                version(),
                era(),
                peer,
                capabilities,
                Map.of());
    }

    /**
     * 把响应负载归一化为内部结果。
     *
     * <p>本时代不识别显式的 {@code resultType} 字段——若存在且非 {@code complete}，视为协议违规。</p>
     *
     * @param result 线格式响应负载
     * @return 归一化结果（{@code resultType} 永远为 {@code COMPLETE}）
     * @throws cn.richie696.component.mcp.protocol.McpProtocolException 当 {@code resultType} 不被本时代支持时
     */
    @Override
    public McpNormalizedResult normalizeResult(Map<String, Object> result) {
        Map<String, Object> payload = result == null ? new LinkedHashMap<>() : new LinkedHashMap<>(result);
        Object resultType = payload.remove("resultType");
        if (resultType == null || "complete".equals(resultType)) {
            return new McpNormalizedResult(McpNormalizedResult.ResultType.COMPLETE, payload);
        }
        throw DialectSupport.invalidParams("Unsupported resultType: " + resultType);
    }

    /**
     * 把内部结果编码为线格式响应。
     *
     * <p>本时代仅支持 {@code COMPLETE} 类型的编码；尝试编码 {@code INPUT_REQUIRED}
     * 会触发 {@link cn.richie696.component.mcp.protocol.McpProtocolException}。</p>
     *
     * @param result 内部结果
     * @return 线格式响应 Map
     * @throws cn.richie696.component.mcp.protocol.McpProtocolException 当结果类型不是 {@code COMPLETE} 时
     */
    @Override
    public Map<String, Object> encodeResult(McpNormalizedResult result) {
        Map<String, Object> wire = new LinkedHashMap<>(result.payload());
        if (result.resultType() != McpNormalizedResult.ResultType.COMPLETE) {
            throw DialectSupport.invalidParams(
                    "Legacy protocol cannot encode resultType: " + result.resultType());
        }
        return Map.copyOf(wire);
    }
}
