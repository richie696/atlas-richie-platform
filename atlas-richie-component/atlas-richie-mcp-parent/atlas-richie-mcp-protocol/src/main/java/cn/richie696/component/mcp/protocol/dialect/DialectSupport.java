package cn.richie696.component.mcp.protocol.dialect;

import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.model.McpImplementationInfo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 方言实现共享的入参解析工具。
 *
 * <p>为什么是包级私有：本类只服务于 {@code dialect} 包内的具体方言实现，外部业务侧
 * 永远不应直接调用。把可见性限制在包内可以避免 API 表面被无意扩大、也避免外部
 * 业务直接使用未脱敏的"入参解析"工具而绕过方言校验。</p>
 *
 * <p>关键设计：
 * <ul>
 *   <li>三个解析方法（{@link #object}/{@link #string}/{@link #implementation}）
 *       都接受 {@code required} 标志，让调用方显式表达"缺这个字段应当报错"还是
 *       "缺这个字段默认空"。</li>
 *   <li>所有错误统一通过 {@link #invalidParams(String)} 抛
 *       {@link McpProtocolException}（错误码 {@code -32602}），与 JSON-RPC 规范
 *       的 "Invalid Params" 语义对齐。</li>
 * </ul>
 * </p>
 *
 * @author richie696
 * @since 2026-08-11
 */
final class DialectSupport {
    private DialectSupport() {
    }

    /**
     * 从 {@code source} 中按 {@code key} 读取一个对象字段。
     *
     * @param source   源 Map
     * @param key      字段名
     * @param required 当为 {@code true} 时，缺失或非对象均报错；为 {@code false} 时缺失返回空 Map
     * @return 不可变的对象 Map（可能为空）
     * @throws McpProtocolException 当字段缺失且必需，或类型不是对象，或包含非字符串键时
     */
    static Map<String, Object> object(Map<String, Object> source, String key, boolean required) {
        Object value = source.get(key);
        if (value == null && !required) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> raw)) {
            throw invalidParams(key + " must be an object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((entryKey, entryValue) -> {
            if (!(entryKey instanceof String stringKey)) {
                throw invalidParams(key + " contains a non-string key");
            }
            result.put(stringKey, entryValue);
        });
        return Collections.unmodifiableMap(result);
    }

    /**
     * 从 {@code source} 中按 {@code key} 读取一个非空字符串字段。
     *
     * @param source   源 Map
     * @param key      字段名
     * @param required 当为 {@code true} 时，缺失或非字符串/空白均报错；为 {@code false} 时缺失返回 {@code null}
     * @return 字符串值
     * @throws McpProtocolException 当字段缺失且必需，或不是非空字符串时
     */
    static String string(Map<String, Object> source, String key, boolean required) {
        Object value = source.get(key);
        if (value == null && !required) {
            return null;
        }
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw invalidParams(key + " must be a non-blank string");
        }
        return stringValue;
    }

    /**
     * 从 {@code source} 中按 {@code key} 读取对端实现信息（{@link McpImplementationInfo}）。
     *
     * @param source   源 Map
     * @param key      字段名
     * @param required 当为 {@code true} 时，缺失或字段非法均报错；为 {@code false} 时缺失返回 {@code null}
     * @return 对端实现信息，可能为 {@code null}
     * @throws McpProtocolException 当字段缺失且必需，或内部校验失败时
     */
    static McpImplementationInfo implementation(Map<String, Object> source, String key, boolean required) {
        Map<String, Object> value = object(source, key, required);
        if (value.isEmpty() && !required) {
            return null;
        }
        try {
            return McpImplementationInfo.fromWire(value);
        } catch (IllegalArgumentException exception) {
            // 复用 -32602 "Invalid Params" 语义，并把内层错误消息透出
            throw invalidParams(key + ": " + exception.getMessage());
        }
    }

    /**
     * 构造一个 "Invalid Params" 错误（JSON-RPC 错误码 {@code -32602}）。
     *
     * @param message 详细错误描述
     * @return 协议层异常
     */
    static McpProtocolException invalidParams(String message) {
        return new McpProtocolException("MCP_INVALID_PARAMS", -32602, message, Map.of());
    }
}
