package cn.richie696.component.mcp.server.tool;

import java.util.Set;

/** Immutable result of an atomic registry mutation. */
/**
 * 注册表原子变更的结果描述：携带新旧版本号以及本次变更加入/移除/更新的 Tool 名称集合。
 *
 * <p>作为 {@link McpToolRegistry#replace(McpToolRegistration)}、
 * {@link McpToolRegistry#replaceAll(java.util.Collection)}、
 * {@link McpToolRegistryChangeListener#onChanged(McpToolRefreshResult)} 等
 * 操作的返回值/事件载荷使用，方便上游做差异推送（例如通知 MCP 客户端哪些工具集发生变化）。</p>
 *
 * @param oldRevision 变更前版本号
 * @param newRevision 变更后版本号；与 oldRevision 相等时表示未发生变更
 * @param addedTools  本次新增的 Tool 名称集合
 * @param removedTools 本次移除的 Tool 名称集合
 * @param updatedTools 本次被替换更新的 Tool 名称集合
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpToolRefreshResult(
        long oldRevision,
        long newRevision,
        Set<String> addedTools,
        Set<String> removedTools,
        Set<String> updatedTools) {

    public McpToolRefreshResult {
        addedTools = addedTools == null ? Set.of() : Set.copyOf(addedTools);
        removedTools = removedTools == null ? Set.of() : Set.copyOf(removedTools);
        updatedTools = updatedTools == null ? Set.of() : Set.copyOf(updatedTools);
    }

    /**
     * 构造一个"无变化"的占位结果，oldRevision 与 newRevision 相同且三个集合均为空。
     *
     * @param revision 当前版本号
     * @return 表示无变更的 {@link McpToolRefreshResult} 实例
     */
    public static McpToolRefreshResult unchanged(long revision) {
        return new McpToolRefreshResult(revision, revision, Set.of(), Set.of(), Set.of());
    }

    /**
     * 判断本次操作是否真正改变了注册表内容。
     *
     * @return true 表示版本号发生变更（即注册表有改动），false 表示无变化
     */
    public boolean changed() {
        return oldRevision != newRevision;
    }
}
