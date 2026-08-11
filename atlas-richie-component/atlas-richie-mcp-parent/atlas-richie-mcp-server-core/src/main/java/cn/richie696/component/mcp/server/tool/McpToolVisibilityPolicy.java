package cn.richie696.component.mcp.server.tool;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;

/**
 * Tool 可见性策略：根据每次调用的授权上下文决定 Tool 是否被当前请求看到、可调用。
 *
 * <p>作为 {@link McpToolRegistry} 的可插拔访问控制点：实现类可以基于 tenant、subject、
 * scope、feature flag 等任意维度过滤 Tool。默认实现 {@link #ALLOW_ALL} 对所有请求放行，
 * 适合开发与单租户场景；生产环境建议组合 {@link McpRequiredScopeVisibilityPolicy} 等
 * 装饰器实现细粒度授权。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@FunctionalInterface
public interface McpToolVisibilityPolicy {
    /**
     * 对所有请求一律放行的默认实现，仅用于开发/单租户场景。
     */
    McpToolVisibilityPolicy ALLOW_ALL = (descriptor, context) -> true;

    /**
     * 判断指定 Tool 在当前调用上下文下是否可见/可调用。
     *
     * @param descriptor 工具描述符
     * @param context    当前调用上下文（含租户、调用者、属性等）
     * @return true 表示可见可调用；false 表示对当前请求隐藏
     */
    boolean isVisible(McpToolDescriptor descriptor, McpCallContext context);
}
