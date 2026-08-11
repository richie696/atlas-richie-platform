package cn.richie696.component.mcp.protocol.discovery;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 完整 MCP 结果的通用缓存元数据工具。
 *
 * <p>在 {@code server/discover} 之外的其他方法响应中，{@code ttlMs} 与 {@code cacheScope}
 * 同样会被消费方用作缓存键与作用域判断。本类提供一个统一的注入入口，避免每个方法
 * 都重复"把两个字段塞进响应 Map"这段逻辑。</p>
 *
 * <p>关键设计：用 {@link java.math.BigDecimal} 承载 {@code ttlMs} 而非 {@link Long}——
 * 部分平台 ObjectMapper 会把 Java {@code Long} 渲染成 JSON 字符串以保护 JS 客户端的
 * 精度，但 MCP 协议要求 {@code ttlMs} 必须是 JSON 数字。{@code BigDecimal} 在 Jackson /
 * Fastjson 下都会按"原始数值"序列化，规避此问题。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpCacheHints {
    /** 缓存键：TTL（毫秒）。 */
    public static final String TTL_MS = "ttlMs";
    /** 缓存键：缓存作用域。 */
    public static final String CACHE_SCOPE = "cacheScope";

    private McpCacheHints() {
    }

    /**
     * 把缓存元数据（{@code ttlMs / cacheScope}）合并到结果 Map 中。
     *
     * @param result 原结果 Map（可为 {@code null}，视为空 Map）
     * @param ttlMs  TTL 毫秒数，必须为非负
     * @param scope  缓存作用域
     * @return 新的、不可写回原引用的 Map
     * @throws IllegalArgumentException 当 {@code ttlMs} 为负时
     */
    public static Map<String, Object> add(Map<String, Object> result, long ttlMs, McpCacheScope scope) {
        if (ttlMs < 0) {
            throw new IllegalArgumentException("ttlMs must be non-negative");
        }
        Map<String, Object> copy = new LinkedHashMap<>(result == null ? Map.of() : result);
        // Preserve the JSON number type even when the host application serializes Long
        // as String to protect JavaScript clients from precision loss.
        copy.put(TTL_MS, java.math.BigDecimal.valueOf(ttlMs));
        copy.put(CACHE_SCOPE, scope.wireValue());
        return copy;
    }
}
