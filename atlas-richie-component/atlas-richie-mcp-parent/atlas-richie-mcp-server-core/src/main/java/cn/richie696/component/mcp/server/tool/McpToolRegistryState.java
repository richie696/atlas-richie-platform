package cn.richie696.component.mcp.server.tool;

import java.util.Collections;
import java.util.NavigableMap;
import java.util.TreeMap;

/** Immutable state observed by one registry operation. */
/**
 * 注册表的一次原子快照：版本号 + 不可变的工具映射。
 *
 * <p>作为 {@link McpToolRegistry} 内部 CAS 操作的目标对象，每次替换都会创建
 * 一个新的 {@link McpToolRegistryState} 实例，保证读侧拿到的始终是某一完整时刻的视图。
 * 内部使用 {@link Collections#unmodifiableNavigableMap} 包装，防止
 * 注册表内部数据结构被外部直接改写破坏不可变语义。</p>
 *
 * @param revision 版本号；自增用于标识"是否发生过变化"
 * @param tools    工具名到已解析 Tool 的不可变 NavigableMap
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpToolRegistryState(
        long revision,
        NavigableMap<String, McpResolvedTool> tools) {

    public McpToolRegistryState {
        tools = Collections.unmodifiableNavigableMap(new TreeMap<>(tools));
    }
}
