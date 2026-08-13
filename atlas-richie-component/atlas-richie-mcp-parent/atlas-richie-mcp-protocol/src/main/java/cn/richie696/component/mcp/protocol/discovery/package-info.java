/**
 * MCP 协议发现（discovery）包：实现 2026-07-28 新增的 {@code server/discover} 方法的请求/响应编解码。
 *
 * <p>设计意图：在 2026-07-28 之前，客户端只能通过 {@code initialize} 握手后才能获取对端能力；
 * 在 2026-07-28 中，协议允许通过 {@code server/discover} 方法在握手前就拿到对端支持的
 * 协议版本、能力和缓存策略（TTL / 缓存作用域），用以驱动
 * {@link cn.richie696.component.mcp.protocol.compatibility.McpEraProbeStateMachine}
 * 的快速路径。本包聚焦该方法的线格式与归一化结果之间的转换。</p>
 *
 * 核心类职责：
 * <ul>
 *   <li>{@link cn.richie696.component.mcp.protocol.discovery.McpDiscoveryCodec}：
 *       {@code server/discover} 方法的请求/响应编解码器，承载协议版本、客户端元数据、
 *       能力声明与缓存提示。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.discovery.McpDiscoverResult}：
 *       {@code server/discover} 的协议无关内部结果（含 extensions 透传通道）。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.discovery.McpCacheScope}：
 *       缓存作用域枚举（PUBLIC / PRIVATE），决定下游 CDN / 浏览器缓存能否复用。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.discovery.McpCacheHints}：
 *       通用缓存元数据（ttlMs / cacheScope）的写入工具，供其他完整结果复用。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
 */
package cn.richie696.component.mcp.protocol.discovery;
