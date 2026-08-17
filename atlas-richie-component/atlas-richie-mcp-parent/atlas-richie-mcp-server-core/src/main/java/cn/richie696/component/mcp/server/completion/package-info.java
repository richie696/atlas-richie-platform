/**
 * Completion（补全）相关类型：MCP 协议中 "completion/complete" 能力的容器。
 *
 * <p>包内核心类与职责：
 * <ul>
 *   <li>{@link cn.richie696.component.mcp.server.completion.McpCompletionRegistry} —
 *       每个 MCP 服务端点对应一个的最薄容器，仅持有
 *       {@link cn.richie696.component.mcp.api.server.McpCompletionHandler}。
 *       按设计每个端点只允许一个补全策略，避免多个补全结果拼接歧义。</li>
 * </ul>
 * 
 *
 * <p>集中在独立包的原因：Completion 能力在当前版本下仅一个容器类，
 * 但 MCP 协议预留了较强的扩展点（argument 路径、ref 资源/prompt 引用等），
 * 未来可能补充更多类型，独立成包便于演进且不污染 Tool/Resource/Prompt 包。
 *
 * @author richie696
 * @since 2026-08-11
 */
package cn.richie696.component.mcp.server.completion;
