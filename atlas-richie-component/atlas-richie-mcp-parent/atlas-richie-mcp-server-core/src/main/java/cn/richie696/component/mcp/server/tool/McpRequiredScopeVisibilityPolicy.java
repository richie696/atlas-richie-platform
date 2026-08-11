package cn.richie696.component.mcp.server.tool;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 必选 scope 装饰器：在已有可见性策略之上叠加 OAuth scope 校验。
 *
 * <p>设计为装饰器模式：保留底层 {@link McpToolVisibilityPolicy} 的判断能力，
 * 同时强制要求调用上下文必须持有 Tool 描述符 {@code requiredScopes} 注解中
 * 列出的所有 scope。已授予的 scope 从 {@link McpCallContext} 的
 * {@code scopes} / {@code scope} 属性中收集（支持字符串或字符串集合两种格式，
 * 字符串按空白字符拆分），避免对上游属性命名做硬编码假设。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpRequiredScopeVisibilityPolicy implements McpToolVisibilityPolicy {
    private final McpToolVisibilityPolicy delegate;

    /**
     * 构造装饰器，包装一个底层策略。
     *
     * @param delegate 被装饰的底层可见性策略，不能为 null
     */
    public McpRequiredScopeVisibilityPolicy(McpToolVisibilityPolicy delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public boolean isVisible(McpToolDescriptor descriptor, McpCallContext context) {
        return delegate.isVisible(descriptor, context)
                && grantedScopes(context).containsAll(requiredScopes(descriptor));
    }

    private Set<String> requiredScopes(McpToolDescriptor descriptor) {
        Object value = descriptor.annotations().get("requiredScopes");
        if (value instanceof Collection<?> collection) {
            Set<String> result = new LinkedHashSet<>();
            for (Object entry : collection) {
                if (entry instanceof String scope && !scope.isBlank()) result.add(scope);
            }
            return result;
        }
        if (value instanceof String scope && !scope.isBlank()) return Set.of(scope);
        return Set.of();
    }

    private Set<String> grantedScopes(McpCallContext context) {
        Set<String> result = new LinkedHashSet<>();
        collect(result, context.attributes().get("scopes"));
        collect(result, context.attributes().get("scope"));
        return result;
    }

    private void collect(Set<String> result, Object value) {
        if (value instanceof Collection<?> collection) {
            for (Object entry : collection) {
                if (entry instanceof String scope && !scope.isBlank()) result.add(scope);
            }
        } else if (value instanceof String scopes) {
            for (String scope : scopes.split("\\s+")) {
                if (!scope.isBlank()) result.add(scope);
            }
        }
    }
}
