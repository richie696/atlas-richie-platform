/**
 * Resource（资源）相关类型：MCP 协议中 "resources" 与 "resources/templates" 能力的注册与解析。
 *
 * <p>包内核心类与职责：
 * <ul>
 *   <li>{@link cn.richie696.component.mcp.server.resource.McpResourceRegistration} —
 *       精确 URI 资源的不可变注册项（描述符 + 读取处理器）。</li>
 *   <li>{@link cn.richie696.component.mcp.server.resource.McpResourceTemplateRegistration} —
 *       URI 模板资源的不可变注册项，支持 RFC 6570 风格的占位符。</li>
 *   <li>{@link cn.richie696.component.mcp.server.resource.McpResourceRegistry} —
 *       资源注册表，同时维护精确表与模板表；解析时优先精确匹配，失败时按模板动态展开，
 *       整个过程受 {@link cn.richie696.component.mcp.server.resource.McpResourceVisibilityPolicy} 控制。</li>
 *   <li>{@link cn.richie696.component.mcp.server.resource.McpResourceVisibilityPolicy} —
 *       基于调用上下文的资源可见性策略 SPI，默认实现 {@code ALLOW_ALL} 一律放行。</li>
 * </ul>
 * 
 *
 * <p>集中在同一个包的原因：精确资源与模板资源共享同一份注册表与可见性策略，
 * 拆分到多个包会导致读者在两个目录间反复跳转；按 MCP 协议中的能力维度聚合更易理解。
 *
 * @author richie696
 * @since 2026-08-11
 */
package cn.richie696.component.mcp.server.resource;
