package cn.richie696.component.mcp.server.tool;

import cn.richie696.component.mcp.api.model.McpToolDescriptor;

import java.util.List;

/**
 * 注册表在某一时刻的只读视图：携带版本号与该版本下对当前请求上下文"可见"的工具描述符集合。
 *
 * <p>每次调用 {@link McpToolRegistry#snapshot(McpCallContext)} 都会基于访问控制策略
 * （{@link McpToolVisibilityPolicy}）过滤一遍，因此不同调用方拿到的列表可能不同。
 * 列表通过 {@link List#copyOf} 防御性拷贝，避免外部修改影响内部状态。</p>
 *
 * @param revision 快照对应的注册表版本号
 * @param tools    对当前调用上下文可见的 Tool 描述符不可变列表
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpToolRegistrySnapshot(long revision, List<McpToolDescriptor> tools) {
    public McpToolRegistrySnapshot {
        tools = List.copyOf(tools);
    }
}
