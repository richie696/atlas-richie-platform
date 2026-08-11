/**
 * MCP 协议兼容层包：在握手尚未完成时，根据对端的探测响应决定走"现代无状态"还是"传统会话"分支。
 *
 * <p>为什么需要这一层：MCP 客户端在尚未与远端完成 {@code initialize} 握手时，无法读取
 * 对方支持的协议版本。探测层会发送一次最小请求（通常 {@code server/discover}），
 * 然后把 HTTP 状态码、JSON-RPC 错误码、对端声明的协议版本列表归一化为
 * {@link cn.richie696.component.mcp.protocol.compatibility.McpProbeEvent}，
 * 再由 {@link cn.richie696.component.mcp.protocol.compatibility.McpEraProbeStateMachine}
 * 给出确定性的 {@link cn.richie696.component.mcp.protocol.compatibility.McpProbeDecision}。</p>
 *
 * <p>关键设计：探测结果会被写入
 * {@link cn.richie696.component.mcp.protocol.compatibility.McpProtocolEraCache}
 * （进程内 TTL 缓存）以避免重复探测，跨实例持久化由应用层自行用
 * {@code platform.cache} 包装。</p>
 *
 * <p>核心类职责：
 * <ul>
 *   <li>{@link cn.richie696.component.mcp.protocol.compatibility.McpTransportBinding}：
 *       传输绑定枚举（STDIO / STREAMABLE_HTTP），决定不同探测分支的判定规则。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.compatibility.McpProbeEvent}：
 *       探测事件的不可变记录，提供五种工厂方法（discover/modern/error/timeout）。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.compatibility.McpProbeDecision}：
 *       状态机输出（era + action + selectedVersion）。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.compatibility.McpEraProbeStateMachine}：
 *       按 transport binding 执行 modern/legacy era 判定的状态机。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.compatibility.McpNegotiatedProtocol}：
 *       单远端已选定的协议版本（含过期时间）。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.compatibility.McpProtocolEraCache}：
 *       进程内 TTL 缓存，无 Redis 依赖。</li>
 * </ul>
 * </p>
 *
 * @author richie696
 * @since 2026-08-11
 */
package cn.richie696.component.mcp.protocol.compatibility;
