package cn.richie696.component.mcp.server.spring.boot;

import cn.richie696.component.mcp.api.server.McpToolDefinitionChangeEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.event.SmartApplicationListener;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * 可选的 Spring Cloud / 自定义事件桥接器，用于原子地刷新 Tool 定义。
 *
 * <p>当引入 Spring Cloud Config / Nacos 等配置中心并启用 {@code EnvironmentChangeEvent} 时，
 * 配置中心推送新配置会产生该事件；本桥接器检测到事件中包含 {@code platform.component.mcp.server.tools}
 * 前缀的 key 变化，就会调用 {@link McpSpringToolDefinitionRefresher#refresh()} 重新注册 Tool。
 * 此外，业务侧也可以直接发布
 * {@link cn.richie696.component.mcp.api.server.McpToolDefinitionChangeEvent}（以 {@link PayloadApplicationEvent}
 * 形式承载）来主动触发刷新。</p>
 *
 * <p>设计上通过 {@link #getOrder()} 将优先级置为较高位，让业务监听器优先消费；
 * 桥接器在刷新失败时仅记录警告并保留既有注册表，确保不会因为配置抖动导致 Tool 整体下线。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpToolRefreshEventBridge implements SmartApplicationListener {
    static final String ENVIRONMENT_CHANGE_EVENT =
            "org.springframework.cloud.context.environment.EnvironmentChangeEvent";
    static final String KEY_PREFIX = McpServerProperties.PREFIX + ".tools";

    private static final System.Logger LOGGER =
            System.getLogger(McpToolRefreshEventBridge.class.getName());

    private final McpSpringToolDefinitionRefresher refresher;

    /**
     * 构造事件桥接器。
     *
     * @param refresher 实际执行刷新的刷新器，可为 {@code null}（{@link #onApplicationEvent} 会跳过）
     */
    public McpToolRefreshEventBridge(McpSpringToolDefinitionRefresher refresher) {
        this.refresher = refresher;
    }

    /**
     * 仅接受两类事件：
     * <ul>
     *     <li>{@code EnvironmentChangeEvent}（按类名字符串匹配，避免硬依赖 Spring Cloud）；</li>
     *     <li>{@link PayloadApplicationEvent}（用于包装自定义的 {@code McpToolDefinitionChangeEvent}）。</li>
     * </ul>
     *
     * @param eventType Spring 容器派发的事件类型
     * @return 若属于上述两类事件则返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return eventType != null && (ENVIRONMENT_CHANGE_EVENT.equals(eventType.getName())
                || PayloadApplicationEvent.class.isAssignableFrom(eventType));
    }

    /**
     * 较高的优先级（{@code Integer.MAX_VALUE - 100}），便于在大多数业务监听器之后执行，
     * 避免抢在业务层之前重建 Tool 视图。
     *
     * @return 监听器顺序常量
     */
    @Override
    public int getOrder() {
        return Integer.MAX_VALUE - 100;
    }

    /**
     * 事件入口：若事件携带的 key 与 MCP Tool 配置相关，则触发刷新。
     *
     * <p>刷新失败时仅记录告警并保留既有注册表状态，绝不让异常冒泡破坏监听链路，
     * 否则会触发 Spring 的事件多播失败处理（可能终止后续监听器）。</p>
     *
     * @param event Spring 派发的事件；类型由 {@link #supportsEventType(Class)} 约束
     */
    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (!shouldRefresh(event)) return;
        try {
            refresher.refresh();
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "MCP tool definition refresh failed; previous registry state retained",
                    exception);
        }
    }

    private boolean shouldRefresh(ApplicationEvent event) {
        if (event instanceof PayloadApplicationEvent<?> payload) {
            return payload.getPayload() instanceof McpToolDefinitionChangeEvent;
        }
        if (!ENVIRONMENT_CHANGE_EVENT.equals(event.getClass().getName())) return false;
        Set<String> keys = extractKeys(event);
        return keys == null || keys.isEmpty()
                || keys.stream().anyMatch(key -> key != null && key.startsWith(KEY_PREFIX));
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractKeys(ApplicationEvent event) {
        try {
            Method method = event.getClass().getMethod("getKeys");
            Object value = method.invoke(event);
            return value instanceof Set<?> set ? (Set<String>) set : null;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}
