package cn.richie696.component.mcp.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将业务方法声明为 MCP Tool。
 *
 * <p>该注解是业务方法暴露为 MCP Tool 的唯一入口：</p>
 * <ol>
 *   <li>启动期由 {@code atlas-richie-mcp-server} 扫描并组装为 {@code McpToolDefinition}；</li>
 *   <li>运行期在 HTTP/SSE 协议适配层根据这些属性生成协议元数据（含 annotations、required scopes 等）。</li>
 * </ol>
 *
 * <p>关键设计：{@link #enabled()} 与 {@link #audit()} 是"配置覆盖前的初始值"，最终是否启用
 * 由部署期 YAML 配置决定——这样的分层让代码默认值（业务安全选择）与运维默认值（部署安全选择）
 * 解耦，运维可以独立决定某个 Tool 在某环境是否开启审计。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpTool {
    /**
     * Tool 业务名，缺省时回落到方法名。
     *
     * @return 业务名
     */
    String name() default "";

    /**
     * 人类可读的标题，会显示在客户端 UI 上。
     *
     * @return 标题
     */
    String title() default "";

    /**
     * 业务描述，提示 LLM 何时应该调用本 Tool。
     *
     * @return 描述文本
     */
    String description() default "";

    /**
     * 是否幂等（多次调用对系统状态影响相同），用于客户端做去重。
     *
     * @return {@code true} 表示幂等
     */
    boolean idempotent() default false;

    /**
     * 是否只读（不修改任何状态），用于客户端做安全告警/二次确认。
     *
     * @return {@code true} 表示只读
     */
    boolean readOnly() default false;

    /**
     * 是否破坏性操作（删除/扣减等不可逆变更），用于客户端做二次确认。
     *
     * @return {@code true} 表示破坏性
     */
    boolean destructive() default false;

    /**
     * 是否依赖开放世界（外部服务/网络），影响超时与重试策略。
     *
     * @return {@code true} 表示开放世界
     */
    boolean openWorld() default false;

    /**
     * 调用所需 Scope 列表，由授权层校验。
     *
     * @return Scope 名数组
     */
    String[] requiredScopes() default {};

    /**
     * Whether the tool is enabled before configuration overrides are applied.
     *
     * <p>代码层默认开关；最终是否暴露由部署期 YAML 覆盖。</p>
     *
     * @return {@code true} 表示代码层默认启用
     */
    boolean enabled() default true;

    /**
     * Optional logical group used by deployment configuration.
     *
     * <p>用于在配置层做批量开关（如同时关闭"高危"组下的所有 Tool）。</p>
     *
     * @return 逻辑分组名
     */
    String group() default "";

    /**
     * Tool-specific timeout in milliseconds; {@code -1} inherits the configured default.
     *
     * <p>{@code -1} 表示沿用全局默认超时；正值则覆盖全局设置，针对长任务/外部依赖场景。</p>
     *
     * @return 超时毫秒数，{@code -1} 表示继承默认
     */
    long timeoutMs() default -1L;

    /**
     * Whether invocation audit is enabled before configuration overrides are applied.
     *
     * <p>代码层默认开关；最终是否记录审计由部署期 YAML 覆盖。</p>
     *
     * @return {@code true} 表示代码层默认开启审计
     */
    boolean audit() default false;
}
