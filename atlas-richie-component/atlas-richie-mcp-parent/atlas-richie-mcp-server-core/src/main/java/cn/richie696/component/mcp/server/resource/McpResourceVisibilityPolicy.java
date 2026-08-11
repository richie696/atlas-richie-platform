package cn.richie696.component.mcp.server.resource;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpResourceDescriptor;

/**
 * Resource 可见性策略：根据调用上下文决定某个 Resource 是否对当前请求可见。
 *
 * <p>作为 {@link McpResourceRegistry} 的可插拔访问控制点；默认 {@link #ALLOW_ALL}
 * 一律放行（开发/单租户场景），生产环境可实现租户隔离、scope 校验等定制策略。
 * 列表过滤与解析过滤都受该策略控制，保证"看不见"与"调不到"语义一致。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@FunctionalInterface
public interface McpResourceVisibilityPolicy {
    /**
     * 对所有请求一律放行的默认实现，仅用于开发/单租户场景。
     */
    McpResourceVisibilityPolicy ALLOW_ALL = (descriptor, context) -> true;

    /**
     * 判断指定 Resource 在当前调用上下文下是否可见。
     *
     * @param descriptor 资源描述符
     * @param context    当前调用上下文
     * @return true 表示可见；false 表示对当前请求隐藏
     */
    boolean isVisible(McpResourceDescriptor descriptor, McpCallContext context);
}
