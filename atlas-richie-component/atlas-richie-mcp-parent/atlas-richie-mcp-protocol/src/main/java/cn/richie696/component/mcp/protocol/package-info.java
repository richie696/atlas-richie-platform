/**
 * MCP 协议核心包：提供跨版本通用的基础设施（异常、协议常量、版本协商、JSON-RPC 校验、Schema 快照）。
 *
 * <p>本包内的类不依赖任何具体协议版本（不调用 {@code dialect/}），是整个 {@code mcp-protocol}
 * 模块的"底座"——{@link cn.richie696.component.mcp.protocol.model}（数据模型）、
 * {@link cn.richie696.component.mcp.protocol.dialect}（协议方言）、
 * {@link cn.richie696.component.mcp.protocol.compatibility}（兼容层）等都建立在这些基础类之上。</p>
 *
 * 关键类职责：
 * <ul>
 *   <li>{@link cn.richie696.component.mcp.protocol.McpProtocolException}：
 *       可无损映射为 JSON-RPC error 的协议层异常，统一错误码语义。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.McpProtocolNegotiator}：
 *       服务端/客户端版本协商门面，偏好顺序由本组件决定。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.McpProtocolVersions}：
 *       已通过验证的协议版本号常量与默认偏好列表。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.McpProtocolEra}：
 *       协议时代枚举（STATELESS_2026 / SESSION_2025），用于在多版本并存时区分配套。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.McpMetaKeys}：
 *       MCP 2026-07-28 保留的协议级 {@code _meta} 键常量，避免魔法字符串。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.McpJsonRpcValidator}：
 *       与版本无关的 JSON-RPC 2.0 入参校验，所有方言进入业务逻辑前的统一闸口。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.McpSchemaSnapshot}：
 *       从 classpath 加载并校验固定版本的官方 JSON Schema（SHA-256 强校验）。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
 */
package cn.richie696.component.mcp.protocol;
