package cn.richie696.component.mcp.protocol.dialect;

import cn.richie696.component.mcp.protocol.McpJsonRpcValidator;
import cn.richie696.component.mcp.protocol.McpMetaKeys;
import cn.richie696.component.mcp.protocol.McpProtocolEra;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.model.McpImplementationInfo;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;
import cn.richie696.component.mcp.protocol.model.McpNormalizedRequest;
import cn.richie696.component.mcp.protocol.model.McpNormalizedResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 2026-07-28 无状态协议时代适配器。
 *
 * <p>关键差异：此时代下，握手信息（{@code protocolVersion / clientInfo / capabilities}）
 * 全部位于 {@code params._meta} 之下。同时响应必须显式带 {@code resultType} 字段
 * （{@code complete} / {@code input_required}）。</p>
 *
 * 强约束：
 * <ul>
 *   <li>{@code _meta.protocolVersion} 必须等于本方言版本，否则按"unsupported version"处理。</li>
 *   <li>若调用方额外传入了 {@code transportProtocolVersion}（如来自 HTTP 头
 *       {@code MCP-Protocol-Version}），则必须与 {@code _meta.protocolVersion} 一致；
 *       不一致时报 {@code MCP_HEADER_MISMATCH}（错误码 {@code -32020}）。</li>
 *   <li>{@code _meta.clientCapabilities} 必传；{@code _meta.clientInfo} 选传。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class Mcp20260728Dialect implements McpProtocolDialect {
    /**
     * 返回该方言对应的协议版本号。
     *
     * @return 恒为 {@link McpProtocolVersions#V_2026_07_28}
     */
    @Override
    public String version() {
        return McpProtocolVersions.V_2026_07_28;
    }

    /**
     * 返回该方言所属的协议时代。
     *
     * @return 恒为 {@link McpProtocolEra#STATELESS_2026}
     */
    @Override
    public McpProtocolEra era() {
        return McpProtocolEra.STATELESS_2026;
    }

    /**
     * 把 JSON-RPC 线格式请求归一化为内部模型。
     *
     * @param request                  线格式请求
     * @param transportProtocolVersion 来自传输头（如 HTTP {@code MCP-Protocol-Version}）的协议版本，可为 {@code null}
     * @return 归一化后的内部请求
     * @throws cn.richie696.component.mcp.protocol.McpProtocolException 当 {@code _meta} 缺失、
     *         协议版本不匹配或 header 与 {@code _meta} 不一致时
     */
    @Override
    public McpNormalizedRequest normalizeRequest(McpJsonRpcRequest request, String transportProtocolVersion) {
        McpJsonRpcValidator.validate(request);
        Map<String, Object> metadata = DialectSupport.object(request.params(), "_meta", true);
        String metadataVersion = DialectSupport.string(metadata, McpMetaKeys.PROTOCOL_VERSION, true);
        if (!version().equals(metadataVersion)) {
            throw unsupportedVersion(metadataVersion);
        }
        if (transportProtocolVersion != null && !metadataVersion.equals(transportProtocolVersion)) {
            throw headerMismatch(metadataVersion, transportProtocolVersion);
        }

        McpImplementationInfo peer = DialectSupport.implementation(metadata, McpMetaKeys.CLIENT_INFO, false);
        Map<String, Object> capabilities =
                DialectSupport.object(metadata, McpMetaKeys.CLIENT_CAPABILITIES, true);
        Map<String, Object> arguments = new LinkedHashMap<>(request.params());
        arguments.remove("_meta");
        return new McpNormalizedRequest(
                request.id(),
                request.method(),
                arguments,
                version(),
                era(),
                peer,
                capabilities,
                metadata);
    }

    /**
     * 把响应负载归一化为内部结果。
     *
     * <p>本时代响应必须显式带 {@code resultType} 字段（{@code complete} 或
     * {@code input_required}），缺失或未知值都按协议违规处理。</p>
     *
     * @param result 线格式响应负载
     * @return 归一化结果
     * @throws cn.richie696.component.mcp.protocol.McpProtocolException 当 {@code resultType} 缺失或非法时
     */
    @Override
    public McpNormalizedResult normalizeResult(Map<String, Object> result) {
        Map<String, Object> payload = result == null ? Map.of() : new LinkedHashMap<>(result);
        String resultType = DialectSupport.string(payload, "resultType", true);
        payload.remove("resultType");
        return new McpNormalizedResult(parseResultType(resultType), payload);
    }

    /**
     * 把内部结果编码为线格式响应。
     *
     * <p>本时代编码会显式写入 {@code resultType}（{@code complete} 或
     * {@code input_required}），下游无需靠"缺省即 complete"推断。</p>
     *
     * @param result 内部结果
     * @return 线格式响应 Map（含 {@code resultType}）
     */
    @Override
    public Map<String, Object> encodeResult(McpNormalizedResult result) {
        Map<String, Object> wire = new LinkedHashMap<>(result.payload());
        wire.put("resultType", switch (result.resultType()) {
            case COMPLETE -> "complete";
            case INPUT_REQUIRED -> "input_required";
        });
        return Map.copyOf(wire);
    }

    private McpNormalizedResult.ResultType parseResultType(String value) {
        return switch (value) {
            case "complete" -> McpNormalizedResult.ResultType.COMPLETE;
            case "input_required" -> McpNormalizedResult.ResultType.INPUT_REQUIRED;
            default -> throw DialectSupport.invalidParams("Unsupported resultType: " + value);
        };
    }

    private McpProtocolException unsupportedVersion(String actual) {
        return new McpProtocolException(
                "MCP_UNSUPPORTED_PROTOCOL_VERSION",
                -32022,
                "Unsupported protocol version",
                Map.of("supported", java.util.List.of(version()), "requested", actual));
    }

    private McpProtocolException headerMismatch(String metadataVersion, String headerVersion) {
        return new McpProtocolException(
                "MCP_HEADER_MISMATCH",
                -32020,
                "MCP-Protocol-Version header does not match request metadata",
                Map.of("metadata", metadataVersion, "header", headerVersion));
    }
}
