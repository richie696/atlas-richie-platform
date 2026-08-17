/**
 * Prompt（提示模板）相关类型：MCP 协议中 "prompts" 能力的注册与解析。
 *
 * <p>包内核心类与职责：
 * <ul>
 *   <li>{@link cn.richie696.component.mcp.server.prompt.McpPromptRegistration} —
 *       Prompt 描述符与渲染处理器绑定的不可变 record。</li>
 *   <li>{@link cn.richie696.component.mcp.server.prompt.McpPromptRegistry} —
 *       确定性 Prompt 注册表，按名称字典序维护；解析阶段会校验描述符中声明的必填参数，
 *       缺失时抛 {@link cn.richie696.component.mcp.protocol.McpProtocolException}。</li>
 * </ul>
 * 
 *
 * <p>集中在同一个包的原因：Prompt 能力在 MCP 协议中粒度较小，仅需"注册项"与"注册表"两类；
 * 拆分为单类包会破坏"按能力聚合"的目录结构一致性。
 *
 * @author richie696
 * @since 2026-08-11
 */
package cn.richie696.component.mcp.server.prompt;
