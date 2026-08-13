package cn.richie696.component.mcp.protocol;

/**
 * MCP 协议时代枚举：标识报文属于哪一种"协议形态世代"。
 *
 * 为什么区分"协议时代"而非仅靠版本号字符串：
 * <ul>
 *   <li>同一版本号可能跨越多个时代（如 2025-11-25 与 2026-07-28 都是
 *       独立版本，但本质属于不同的协议形态）。</li>
 *   <li>兼容层（{@code compatibility/}）需要按"现代无状态 vs 传统会话"分支决策，
 *       把"时代"作为独立维度让状态机判定更可读、更易扩展。</li>
 *   <li>{@code STATELESS_2026} 表示 2026-07-28 的无状态协议形态（无 initialize 握手、
 *       协议版本走 {@code _meta} 透传）。</li>
 *   <li>{@code SESSION_2025} 表示 2025-11-25 的会话/initialize 协议形态（基于
 *       {@code initialize}/{@code initialized} 握手建立会话）。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
 */
public enum McpProtocolEra {
    /** 2026-07-28 起的无状态协议形态。 */
    STATELESS_2026,
    /** 2025-11-25 起的会话/initialize 协议形态。 */
    SESSION_2025
}
