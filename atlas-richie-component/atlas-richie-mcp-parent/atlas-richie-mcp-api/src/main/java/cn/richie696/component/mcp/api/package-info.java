/**
 * MCP（Model Context Protocol）组件对外暴露的稳定 API 入口包。
 *
 * <p>本包内的类型共同构成"MCP 业务门面"——业务侧只需依赖本包即可与任意 MCP Server
 * 进行交互，而不必关心底层传输协议、协议版本协商、客户端缓存等实现细节。
 *
 * <p>核心类型与各自职责：
 * <ul>
 *   <li>{@link cn.richie696.component.mcp.api.McpOperations}：以 {@code serverId} 为索引的静态调用门面，
 *       适用于在配置中预声明的 MCP Server。</li>
 *   <li>{@link cn.richie696.component.mcp.api.McpDynamicOperations}：每次调用携带 {@link
 *       cn.richie696.component.mcp.api.McpClientRequest}（端点 + 凭证），适用于发现式客户端。</li>
 *   <li>{@link cn.richie696.component.mcp.api.McpClientRequest}：Discovery-driven 客户端的单次请求描述，
 *       包含目标端点与 HTTP 头部（认证信息等）。</li>
 *   <li>{@link cn.richie696.component.mcp.api.McpCallContext}：Server 与 Client 共用的稳定调用上下文，
 *       透传租户、主体、协议版本、取消令牌、进度回报等横切关注点。</li>
 *   <li>{@link cn.richie696.component.mcp.api.McpCancellationToken}：与传输无关的协作式取消信号。</li>
 *   <li>{@link cn.richie696.component.mcp.api.McpProgressReporter}：Tool 调用进度回报端口。</li>
 *   <li>{@link cn.richie696.component.mcp.api.McpInputProvider}：Client 侧 MRTR 多轮输入采集回调。</li>
 *   <li>{@link cn.richie696.component.mcp.api.McpException}：MCP 组件对业务暴露的统一异常基类。</li>
 *   <li>{@link cn.richie696.component.mcp.api.McpCallCancelledException}：调用被取消的特定异常。</li>
 * </ul>
 *
 * <p>将本包单独抽出的意义在于：它定义了一套与传输层、协议版本解耦的稳定 API。
 * 上层业务只面向这些类型编程，协议升级（HTTP/SSE/Stdio）或扩展（多租户、多 Scope）
 * 都不会破坏既有调用方。
 *
 * @author richie696
 * @since 2026-08-11
 */
package cn.richie696.component.mcp.api;
