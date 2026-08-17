/**
 * Tool 调用调度与治理拦截器：MCP 服务端 "tools/call" 请求的中央执行路径。
 *
 * <p>包内核心类与职责：
 * <ul>
 *   <li>{@link cn.richie696.component.mcp.server.dispatch.McpToolDispatcher} —
 *       调度器核心，按"入参校验 → 拦截器链 → 业务 handler → 出参校验 → 异常边界"流水线执行；
 *       拦截器按 {@link cn.richie696.component.mcp.api.server.McpToolInvocationInterceptor#order()}
 *       升序串联。</li>
 *   <li>{@link cn.richie696.component.mcp.server.dispatch.McpTimeoutInvocationInterceptor} —
 *       内置超时拦截器，把 {@link java.util.concurrent.TimeoutException} 翻译为
 *       {@code MCP_TOOL_TIMEOUT}（错误码 -32001）。</li>
 *   <li>{@link cn.richie696.component.mcp.server.dispatch.McpAuditInvocationInterceptor} —
 *       内置审计拦截器，在 Tool 声明 {@code audit=true} 时写入脱敏后的审计事件；
 *       审计失败被吞掉，绝不影响业务结果。</li>
 * </ul>
 * 
 *
 * <p>集中在同一个包的原因：调度器与内置拦截器是同一执行链路的协作组件，
 * 拆分会强制读者跨包理解"调用如何流转"；按"调用调度"这一横切关注点聚合更内聚。
 *
 * @author richie696
 * @since 2026-08-11
 */
package cn.richie696.component.mcp.server.dispatch;
