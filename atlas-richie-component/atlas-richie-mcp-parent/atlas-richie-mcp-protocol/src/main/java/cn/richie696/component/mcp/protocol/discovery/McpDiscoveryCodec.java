package cn.richie696.component.mcp.protocol.discovery;

import cn.richie696.component.mcp.protocol.McpMetaKeys;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.dialect.Mcp20260728Dialect;
import cn.richie696.component.mcp.protocol.model.McpImplementationInfo;
import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;
import cn.richie696.component.mcp.protocol.model.McpNormalizedRequest;
import cn.richie696.component.mcp.protocol.model.McpNormalizedResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 2026-07-28 {@code server/discover} 方法的请求/响应编解码器。
 *
 * <p>为什么独立建 codec：{@code server/discover} 是 2026-07-28 新增的"握手前探测"方法，
 * 其请求与响应字段集（{@code supportedVersions / capabilities / ttlMs / cacheScope}）与其他
 * 业务方法差异较大，复用通用编解码会让代码难读。把 {@code _meta} 注入、{@code ttlMs} 强
 * 校验、缓存作用域枚举转换等逻辑集中在本类，业务侧只调用 {@link #encodeRequest} /
 * {@link #encodeResult} / {@link #decodeRequest} / {@link #decodeResult} 四个对称方法即可。</p>
 *
 * 关键设计：
 * <ul>
 *   <li>{@code ttlMs} 用 {@link java.math.BigDecimal} 承载以确保 JSON 序列化保留数字类型
 *       （与 {@link McpCacheHints} 同理）。</li>
 *   <li>解码时执行多重约束：方法名必须为 {@link #METHOD}、不允许业务参数、必须为带 id 的
 *       请求、响应类型必须为 {@code COMPLETE}。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpDiscoveryCodec {
    /** {@code server/discover} 方法名常量。 */
    public static final String METHOD = "server/discover";

    private final Mcp20260728Dialect dialect;

    /**
     * 使用默认 2026-07-28 方言构造编解码器。
     */
    public McpDiscoveryCodec() {
        this(new Mcp20260728Dialect());
    }

    /**
     * 使用注入的方言构造编解码器（便于测试）。
     *
     * @param dialect 2026-07-28 方言实现
     */
    public McpDiscoveryCodec(Mcp20260728Dialect dialect) {
        this.dialect = dialect;
    }

    /**
     * 把客户端身份/能力编码为 {@code server/discover} 请求。
     *
     * @param id                请求 id
     * @param clientInfo        客户端实现信息，可为 {@code null}
     * @param clientCapabilities 客户端能力声明，可为 {@code null}（视为空 Map）
     * @return 线格式 JSON-RPC 请求
     */
    public McpJsonRpcRequest encodeRequest(
            Object id,
            McpImplementationInfo clientInfo,
            Map<String, Object> clientCapabilities) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(McpMetaKeys.PROTOCOL_VERSION, McpProtocolVersions.V_2026_07_28);
        metadata.put(McpMetaKeys.CLIENT_CAPABILITIES,
                clientCapabilities == null ? Map.of() : clientCapabilities);
        if (clientInfo != null) {
            metadata.put(McpMetaKeys.CLIENT_INFO, clientInfo.toWire());
        }
        return new McpJsonRpcRequest("2.0", id, METHOD, Map.of("_meta", metadata));
    }

    /**
     * 把线格式请求解码为内部模型，并执行 {@code server/discover} 特有的强约束。
     *
     * @param request                  线格式请求
     * @param transportProtocolVersion 传输层协议版本（可为 {@code null}）
     * @return 归一化后的内部请求
     * @throws McpProtocolException 当方法名不符、含业务参数或为通知型时
     */
    public McpNormalizedRequest decodeRequest(
            McpJsonRpcRequest request,
            String transportProtocolVersion) {
        McpNormalizedRequest normalized = dialect.normalizeRequest(request, transportProtocolVersion);
        if (!METHOD.equals(normalized.method())) {
            throw invalidParams("Expected method " + METHOD + " but received " + normalized.method());
        }
        if (!normalized.arguments().isEmpty()) {
            throw invalidParams("server/discover accepts no parameters beyond _meta");
        }
        if (normalized.notification()) {
            throw invalidParams("server/discover must be a request with an id");
        }
        return normalized;
    }

    /**
     * 把内部结果编码为线格式响应。
     *
     * @param result 内部 {@code server/discover} 结果
     * @return 线格式响应 Map（含 {@code resultType}）
     */
    public Map<String, Object> encodeResult(McpDiscoverResult result) {
        Map<String, Object> payload = new LinkedHashMap<>(result.extensions());
        payload.put("supportedVersions", result.supportedVersions());
        payload.put("capabilities", result.capabilities());
        if (result.serverInfo() != null) {
            payload.put("_meta", Map.of(McpMetaKeys.SERVER_INFO, result.serverInfo().toWire()));
        }
        if (result.instructions() != null) {
            payload.put("instructions", result.instructions());
        }
        // Some platform ObjectMapper configurations intentionally serialize Java Long
        // values as JSON strings for browser precision. MCP requires ttlMs to remain a
        // JSON integer, so use an arbitrary-precision numeric carrier on the wire.
        payload.put("ttlMs", java.math.BigDecimal.valueOf(result.ttlMs()));
        payload.put("cacheScope", result.cacheScope().wireValue());
        return dialect.encodeResult(
                new McpNormalizedResult(McpNormalizedResult.ResultType.COMPLETE, payload));
    }

    /**
     * 把线格式响应解码为内部结果。
     *
     * @param wireResult 线格式响应 Map
     * @return 内部 {@code server/discover} 结果
     * @throws McpProtocolException 当字段缺失、类型错误或 {@code resultType} 非 {@code complete} 时
     */
    public McpDiscoverResult decodeResult(Map<String, Object> wireResult) {
        McpNormalizedResult normalized = dialect.normalizeResult(wireResult);
        if (normalized.resultType() != McpNormalizedResult.ResultType.COMPLETE) {
            throw invalidParams("server/discover must return resultType complete");
        }

        Map<String, Object> payload = new LinkedHashMap<>(normalized.payload());
        List<String> versions = stringList(payload.remove("supportedVersions"), "supportedVersions");
        Map<String, Object> capabilities = object(payload.remove("capabilities"), "capabilities", true);
        Map<String, Object> metadata = object(payload.remove("_meta"), "_meta", false);
        McpImplementationInfo serverInfo = implementation(
                metadata.get(McpMetaKeys.SERVER_INFO),
                McpMetaKeys.SERVER_INFO,
                false);
        String instructions = optionalString(payload.remove("instructions"), "instructions");
        long ttlMs = nonNegativeInteger(payload.remove("ttlMs"), "ttlMs");
        McpCacheScope cacheScope;
        try {
            cacheScope = McpCacheScope.fromWireValue(
                    requiredString(payload.remove("cacheScope"), "cacheScope"));
        } catch (IllegalArgumentException exception) {
            throw invalidParams(exception.getMessage());
        }
        return new McpDiscoverResult(
                versions,
                capabilities,
                serverInfo,
                instructions,
                ttlMs,
                cacheScope,
                payload);
    }

    private List<String> stringList(Object value, String field) {
        if (!(value instanceof List<?> raw) || raw.isEmpty()) {
            throw invalidParams(field + " must be a non-empty array");
        }
        List<String> result = new ArrayList<>(raw.size());
        for (Object entry : raw) {
            result.add(requiredString(entry, field + "[]"));
        }
        return List.copyOf(result);
    }

    private Map<String, Object> object(Object value, String field, boolean required) {
        if (value == null && !required) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> raw)) {
            throw invalidParams(field + " must be an object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, entryValue) -> {
            if (!(key instanceof String stringKey)) {
                throw invalidParams(field + " contains a non-string key");
            }
            result.put(stringKey, entryValue);
        });
        return Collections.unmodifiableMap(result);
    }

    private McpImplementationInfo implementation(Object value, String field, boolean required) {
        if (value == null && !required) {
            return null;
        }
        Map<String, Object> object = object(value, field, required);
        try {
            return McpImplementationInfo.fromWire(object);
        } catch (IllegalArgumentException exception) {
            throw invalidParams(field + ": " + exception.getMessage());
        }
    }

    private String requiredString(Object value, String field) {
        if (!(value instanceof String string) || string.isBlank()) {
            throw invalidParams(field + " must be a non-blank string");
        }
        return string;
    }

    private String optionalString(Object value, String field) {
        return value == null ? null : requiredString(value, field);
    }

    private long nonNegativeInteger(Object value, String field) {
        if (!(value instanceof Number number)) {
            throw invalidParams(field + " must be a number");
        }
        double decimal = number.doubleValue();
        long result = number.longValue();
        if (!Double.isFinite(decimal) || decimal < 0 || decimal != result) {
            throw invalidParams(field + " must be a non-negative integer");
        }
        return result;
    }

    private McpProtocolException invalidParams(String message) {
        return new McpProtocolException("MCP_INVALID_PARAMS", -32602, message, Map.of());
    }
}
