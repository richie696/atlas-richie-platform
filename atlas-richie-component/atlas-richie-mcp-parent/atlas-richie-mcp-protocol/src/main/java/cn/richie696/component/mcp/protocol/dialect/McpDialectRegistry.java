package cn.richie696.component.mcp.protocol.dialect;

import cn.richie696.component.mcp.protocol.McpProtocolException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 以协议版本为键选择 Dialect；升级协议时新增实现并注册即可。
 *
 * <p>设计意图：协议版本升级（如未来出现 2027-xx-xx）只需新增一个
 * {@link McpProtocolDialect} 实现并注册到本类，无需改动任何业务侧代码。
 * 反之，移除旧版本方言时也仅需从注册集合中剔除。</p>
 *
 * 关键设计：
 * <ul>
 *   <li>默认注册 2026-07-28 与 2025-11-25 两个方言，顺序不影响查询（按版本号索引）。</li>
 *   <li>注册期检测重复版本号——同一版本号被多次注册属于配置错误，构造时直接拒绝。</li>
 *   <li>查询失败抛 {@link McpProtocolException}（错误码 {@code -32022}），与协议层
 *       "Unsupported Protocol Version" 语义对齐。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpDialectRegistry {
    private final Map<String, McpProtocolDialect> dialects;

    /**
     * 使用默认方言集（{@link Mcp20260728Dialect} + {@link Mcp20251125Dialect}）构造注册表。
     */
    public McpDialectRegistry() {
        this(List.of(new Mcp20260728Dialect(), new Mcp20251125Dialect()));
    }

    /**
     * 使用自定义方言集合构造注册表。
     *
     * @param dialects 待注册方言集合
     * @throws IllegalArgumentException 当同一协议版本被多次注册时
     */
    public McpDialectRegistry(Collection<? extends McpProtocolDialect> dialects) {
        Objects.requireNonNull(dialects, "dialects");
        Map<String, McpProtocolDialect> indexed = new LinkedHashMap<>();
        for (McpProtocolDialect dialect : dialects) {
            McpProtocolDialect duplicate = indexed.put(dialect.version(), dialect);
            if (duplicate != null) {
                throw new IllegalArgumentException("Duplicate MCP dialect: " + dialect.version());
            }
        }
        this.dialects = Map.copyOf(indexed);
    }

    /**
     * 按协议版本号获取方言实现，缺失则抛 {@link McpProtocolException}。
     *
     * @param version 协议版本号
     * @return 对应的方言实现
     * @throws McpProtocolException 当指定版本未被注册时
     */
    public McpProtocolDialect require(String version) {
        McpProtocolDialect dialect = dialects.get(version);
        if (dialect == null) {
            throw new McpProtocolException(
                    "MCP_UNSUPPORTED_PROTOCOL_VERSION",
                    -32022,
                    "No dialect registered for MCP protocol version: " + version,
                    Map.of("supportedVersions", dialects.keySet()));
        }
        return dialect;
    }

    /**
     * 返回已注册的所有方言实例。
     *
     * @return 方言集合
     */
    public Collection<McpProtocolDialect> dialects() {
        return dialects.values();
    }
}
