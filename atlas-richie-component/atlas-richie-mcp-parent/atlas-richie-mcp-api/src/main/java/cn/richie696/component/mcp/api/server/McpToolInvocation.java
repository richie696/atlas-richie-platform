package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable invocation visible to framework-neutral governance interceptors.
 *
 * <p>该 record 是一次 Tool 调用的不可变快照，承载了拦截器决策所需的全部信息：
 * Tool 描述、已绑定入参、调用上下文、最终超时、合并后的策略以及目标处理器。
 * "不可变"是核心约束：拦截器链上的所有节点共享同一份 invocation，互不干扰，
 * 任何修改都要通过返回新 invocation 完成，避免在并发场景下出现竞态。</p>
 *
 * @param tool Tool 描述
 * @param arguments 已绑定入参
 * @param context 业务调用上下文
 * @param timeout 最终超时
 * @param policies 合并后的策略
 * @param handler 目标处理器
 * @author richie696
 * @since 2026-08-11
 */
public record McpToolInvocation(
        McpToolDescriptor tool,
        Map<String, Object> arguments,
        McpCallContext context,
        Duration timeout,
        Map<String, Object> policies,
        McpToolHandler handler) {

    /**
     * 紧凑构造器：对所有非空必填字段做 NPE 校验，对 Map 字段做不可变拷贝。
     *
     * @param tool Tool 描述
     * @param arguments 已绑定入参
     * @param context 业务调用上下文
     * @param timeout 最终超时
     * @param policies 合并后的策略
     * @param handler 目标处理器
     * @throws NullPointerException 当 {@code tool}/{@code context}/{@code handler} 任一为 {@code null} 时
     */
    public McpToolInvocation {
        tool = Objects.requireNonNull(tool, "tool");
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        context = Objects.requireNonNull(context, "context");
        policies = policies == null ? Map.of() : Map.copyOf(policies);
        handler = Objects.requireNonNull(handler, "handler");
    }
}
