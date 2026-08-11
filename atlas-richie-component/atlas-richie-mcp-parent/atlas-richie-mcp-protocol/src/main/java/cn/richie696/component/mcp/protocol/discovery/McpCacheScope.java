package cn.richie696.component.mcp.protocol.discovery;

/**
 * 缓存作用域枚举：标识一份完整结果可被哪些缓存层共享。
 *
 * <p>为什么只有两级：MCP 协议在缓存层只关心"能否被中间层（如 CDN、反向代理、浏览器）
 * 共享"。{@code PUBLIC} 表示响应与主体身份无关、可被任意中间层缓存；{@code PRIVATE}
 * 表示与主体身份强相关，只能被终端缓存。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public enum McpCacheScope {
    /** 可被任何中间层共享缓存。 */
    PUBLIC("public"),
    /** 仅可被终端缓存，不能被中间层共享。 */
    PRIVATE("private");

    private final String wireValue;

    McpCacheScope(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * 返回线格式字面量。
     *
     * @return {@code "public"} 或 {@code "private"}
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * 从线格式字面量反查枚举。
     *
     * @param value 线格式字符串
     * @return 对应枚举值
     * @throws IllegalArgumentException 当值非 {@code "public"}/{@code "private"} 时
     */
    public static McpCacheScope fromWireValue(String value) {
        return switch (value) {
            case "public" -> PUBLIC;
            case "private" -> PRIVATE;
            default -> throw new IllegalArgumentException("Unsupported MCP cache scope: " + value);
        };
    }
}
