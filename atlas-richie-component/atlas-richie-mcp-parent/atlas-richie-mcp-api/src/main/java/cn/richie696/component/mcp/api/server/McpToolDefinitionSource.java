package cn.richie696.component.mcp.api.server;

import java.util.Collection;

/**
 * Loads tool definitions from one deterministic source.
 *
 * <p>"确定性来源"是指同一次 {@link #load()} 调用在外部状态不变时总是返回相同结果，
 * 框架据此在启动期 + 变更事件触发时重新合并出最终的 {@link McpToolDefinition} 列表。
 * 一个应用可以注册多个 Source（如注解扫描 + Nacos + 业务方自定义），按 {@link #order()}
 * 升序合并。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public interface McpToolDefinitionSource {
    /**
     * 来源标识，用于在多源合并场景中定位是哪一份源贡献了某个 Tool。
     *
     * @return 不可空且唯一的源标识
     */
    String sourceId();

    /**
     * 来源优先级，值越小越先参与合并。多个源产生同名 Tool 时，框架按此字段决定覆盖顺序。
     *
     * @return 排序值，默认 {@code 0}
     */
    default int order() {
        return 0;
    }

    /**
     * 加载本源当前的全部 Tool 定义。
     *
     * <p>实现方应保证该方法可重复调用且对系统状态无副作用；耗时操作（如远程拉取）
     * 建议自行加缓存并通过 {@link McpToolDefinitionChangeEvent} 通知缓存失效。</p>
     *
     * @return 当前 Tool 定义集合
     */
    Collection<McpToolDefinition> load();
}
