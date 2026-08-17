package cn.richie696.component.mcp.server.spring.boot;

import cn.richie696.component.mcp.server.tool.McpToolRefreshResult;
import cn.richie696.component.mcp.server.tool.McpToolRegistry;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 重新绑定当前 Spring {@link Environment} 中的 MCP 配置，并原子地刷新 Tool 注册表。
 *
 * <p>这是 Spring 场景下 {@code @McpTool} 与 {@code tools.definitions} / {@code tools.overrides}
 * 配置的运行时刷新入口。刷新过程：
 * <ol>
 *     <li>通过 {@link Binder} 从 {@link Environment} 重新绑定 {@link McpServerProperties}；</li>
 *     <li>将新配置推入 {@link McpToolRegistrationAssembler}；</li>
 *     <li>调用 {@link McpToolRegistry#replaceAll(java.util.Collection)} 完成原子替换；</li>
 *     <li>无论成败，均更新最近一次 {@link McpToolRefreshStatus} 用于运维查询。</li>
 * </ol>
 * 失败时不会破坏既有注册表（保留上一次成功状态），仅记录失败原因。
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpSpringToolDefinitionRefresher {
    private final McpToolRegistry registry;
    private final McpToolRegistrationAssembler assembler;
    private final Environment environment;
    private final McpServerProperties initialProperties;
    private final AtomicReference<McpToolRefreshStatus> status;

    /**
     * 构造刷新器并以当前注册表版本初始化状态。
     *
     * @param registry 目标 MCP Tool 注册表，不可为 {@code null}
     * @param assembler 负责合并多源 Tool 注册的装配器，不可为 {@code null}
     * @param environment Spring 环境（用于重新绑定配置），不可为 {@code null}
     * @param initialProperties 启动时的初始配置，作为绑定失败的回退值，不可为 {@code null}
     * @throws NullPointerException 当任一参数为 {@code null} 时
     */
    public McpSpringToolDefinitionRefresher(
            McpToolRegistry registry,
            McpToolRegistrationAssembler assembler,
            Environment environment,
            McpServerProperties initialProperties) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.assembler = Objects.requireNonNull(assembler, "assembler");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.initialProperties = Objects.requireNonNull(initialProperties, "initialProperties");
        this.status = new AtomicReference<>(new McpToolRefreshStatus(
                true, registry.revision(), Instant.now(), null));
    }

    /**
     * 同步执行一次刷新。
     *
     * <p>整个方法在锁内串行化，保证同一时刻只有一个刷新任务进行；并发调用会排队等待。
     * 失败时异常会原样抛出，调用方应捕获并降级（注册表保持上一次成功状态）。</p>
     *
     * @return 注册表原子替换结果，包含新增/移除的 Tool 列表与新版本号
     * @throws RuntimeException 当绑定或装配过程失败时（失败信息已同步写入 {@link #status()}）
     */
    public synchronized McpToolRefreshResult refresh() {
        Instant attemptedAt = Instant.now();
        try {
            McpServerProperties rebound = Binder.get(environment)
                    .bind(McpServerProperties.PREFIX, Bindable.of(McpServerProperties.class))
                    .orElse(initialProperties);
            assembler.updateProperties(rebound);
            McpToolRefreshResult result = registry.replaceAll(assembler.assemble());
            status.set(new McpToolRefreshStatus(
                    true, result.newRevision(), attemptedAt, null));
            return result;
        } catch (RuntimeException exception) {
            status.set(new McpToolRefreshStatus(
                    false, registry.revision(), attemptedAt, safeMessage(exception)));
            throw exception;
        }
    }

    /**
     * 拉取最近一次刷新的状态快照。
     *
     * @return 最近一次 {@link #refresh()} 的执行结果；启动后初始状态记为成功（{@code successful=true}）
     */
    public McpToolRefreshStatus status() {
        return status.get();
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
