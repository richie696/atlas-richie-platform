package cn.richie696.component.mcp.protocol.compatibility;

/**
 * 传输绑定枚举：标识 MCP 协议运行在哪种传输之上。
 *
 * <p>为什么探测状态机需要知道传输：传统协议时代仅在 {@code STDIO} 传输下被广泛支持——
 * 现代 HTTP 传输的 {@code server/discover} 探测一旦失败就基本可以判定对端不兼容，
 * 而 STDIO 失败时仍可能需要降级到传统 {@code initialize} 握手。传输类型是状态机
 * 决策的关键维度。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public enum McpTransportBinding {
    /** 进程间管道传输（如子进程 stdin/stdout）。 */
    STDIO,
    /** Streamable HTTP 传输。 */
    STREAMABLE_HTTP
}
