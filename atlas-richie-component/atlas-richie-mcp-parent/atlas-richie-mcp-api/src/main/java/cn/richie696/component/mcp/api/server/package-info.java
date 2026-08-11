/**
 * MCP Server 端稳定业务面：定义业务方法暴露成 MCP Tool 所需要的全部契约 SPI。
 *
 * <p>本包是 {@code atlas-richie-mcp-server} 实现层的"业务侧接口"，目的有两个：</p>
 * <ol>
 *   <li>让业务开发者只需要面向这些接口编程：定义 {@code @McpTool} 业务方法、
 *       必要时提供 {@link cn.richie696.component.mcp.api.server.McpToolHandlerProvider}、
 *       {@link cn.richie696.component.mcp.api.server.McpToolDefinitionSource}、{@link
 *       cn.richie696.component.mcp.api.server.McpToolAuditSink} 等扩展点。</li>
 *   <li>把协议层（HTTP/SSE/Stdio）放在更底层实现，避免业务实现被协议细节污染。</li>
 * </ol>
 *
 * <p>按职责归类本包内的类型：</p>
 * <ul>
 *   <li>Tool 元数据与定义：
 *       {@link cn.richie696.component.mcp.api.server.McpToolDefinition}、{@link
 *       cn.richie696.component.mcp.api.server.McpToolDefinitionSource}、{@link
 *       cn.richie696.component.mcp.api.server.McpToolDefinitionChangeEvent}。</li>
 *   <li>Tool 执行与拦截链：
 *       {@link cn.richie696.component.mcp.api.server.McpToolHandler}、{@link
 *       cn.richie696.component.mcp.api.server.McpToolHandlerProvider}、{@link
 *       cn.richie696.component.mcp.api.server.McpToolInvocation}、{@link
 *       cn.richie696.component.mcp.api.server.McpToolInvocationChain}、{@link
 *       cn.richie696.component.mcp.api.server.McpToolInvocationInterceptor}。</li>
 *   <li>参数绑定：
 *       {@link cn.richie696.component.mcp.api.server.McpArgumentBinder}、{@link
 *       cn.richie696.component.mcp.api.server.McpArgumentMetadata}、{@link
 *       cn.richie696.component.mcp.api.server.McpArgumentBindingException}。</li>
 *   <li>调用上下文：
 *       {@link cn.richie696.component.mcp.api.server.McpCallContextFactory}、{@link
 *       cn.richie696.component.mcp.api.server.McpServerCallContextRequest}。</li>
 *   <li>Resource / Prompt / Completion Handler：
 *       {@link cn.richie696.component.mcp.api.server.McpResourceHandler}、{@link
 *       cn.richie696.component.mcp.api.server.McpPromptHandler}、{@link
 *       cn.richie696.component.mcp.api.server.McpCompletionHandler}、{@link
 *       cn.richie696.component.mcp.api.server.McpCompletionRequest}。</li>
 *   <li>执行异常与审计：
 *       {@link cn.richie696.component.mcp.api.server.McpToolExecutionException}、{@link
 *       cn.richie696.component.mcp.api.server.McpToolAuditEvent}、{@link
 *       cn.richie696.component.mcp.api.server.McpToolAuditSink}。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
 */
package cn.richie696.component.mcp.api.server;
