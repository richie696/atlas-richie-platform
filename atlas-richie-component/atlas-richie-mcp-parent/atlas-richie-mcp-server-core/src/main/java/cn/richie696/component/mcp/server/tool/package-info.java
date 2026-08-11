/**
 * Tool（工具）相关类型：MCP 协议中 "tools" 能力的注册、解析、可见性策略与变更通知。
 *
 * <p>包内核心类与职责：
 * <ul>
 *   <li>{@link cn.richie696.component.mcp.server.tool.McpToolRegistration} —
 *       把工具描述符与执行处理器绑定的不可变 record。</li>
 *   <li>{@link cn.richie696.component.mcp.server.tool.McpResolvedTool} —
 *       注册表内部缓存的"已解析形态"，把入参/出参 JSON Schema 预编译为
 *       {@link cn.richie696.component.mcp.schema.McpCompiledSchema}，避免请求热路径重复解析。</li>
 *   <li>{@link cn.richie696.component.mcp.server.tool.McpToolRegistry} —
 *       线程安全、原子可替换的工具注册表，支持 register / unregister / replace / replaceAll /
 *       snapshot 等操作，并通过 {@link cn.richie696.component.mcp.server.tool.McpToolRegistryChangeListener}
 *       通知变更。</li>
 *   <li>{@link cn.richie696.component.mcp.server.tool.McpToolVisibilityPolicy} —
 *       基于调用上下文的可见性策略 SPI；{@link cn.richie696.component.mcp.server.tool.McpRequiredScopeVisibilityPolicy}
 *       是其装饰器实现，按 Tool 声明的 requiredScopes 注解强制 OAuth scope 校验。</li>
 *   <li>{@link cn.richie696.component.mcp.server.tool.McpToolRegistryState} /
 *       {@link cn.richie696.component.mcp.server.tool.McpToolRegistrySnapshot} /
 *       {@link cn.richie696.component.mcp.server.tool.McpToolRefreshResult} —
 *       不可变状态、快照与变更差异描述，用于 CAS 替换与变更广播。</li>
 * </ul>
 * </p>
 *
 * <p>这些类集中在同一个包的原因：它们共同构成 Tool 能力的"内存模型"，
 * 缺一不可；按"能力"而非"层级"分包，便于阅读时一次性理解 Tool 子系统的全部契约。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
package cn.richie696.component.mcp.server.tool;
