package cn.richie696.component.mcp.protocol;

/**
 * MCP 2026-07-28 保留的协议级 {@code _meta} 键。
 *
 * <p>为什么单独建常量类：MCP 2026-07-28 把 {@code protocolVersion / clientInfo /
 * clientCapabilities} 等元信息从 {@code initialize} 请求的顶层字段搬到了 {@code params._meta}
 * 之下，键名采用 {@code io.modelcontextprotocol/} 命名空间。这些字符串一旦写错会导致
 * 整个握手失败，但靠人来记是脆弱的——本类提供唯一定义点，避免魔法字符串与笔误。</p>
 *
 * 关键设计：
 * <ul>
 *   <li>{@link #PROGRESS_TOKEN} 不在 {@code io.modelcontextprotocol/} 命名空间下——
 *       它是 JSON-RPC 2.0 规范中通用的"进度通知令牌"键，故保留原样。</li>
 *   <li>构造器私有，确保仅作常量容器使用。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpMetaKeys {
    /** {@code _meta.io.modelcontextprotocol/protocolVersion}：本次通信使用的协议版本。 */
    public static final String PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion";
    /** {@code _meta.io.modelcontextprotocol/clientInfo}：客户端实现身份（名称、版本、图标等）。 */
    public static final String CLIENT_INFO = "io.modelcontextprotocol/clientInfo";
    /** {@code _meta.io.modelcontextprotocol/clientCapabilities}：客户端能力声明。 */
    public static final String CLIENT_CAPABILITIES = "io.modelcontextprotocol/clientCapabilities";
    /** {@code _meta.io.modelcontextprotocol/serverInfo}：服务端实现身份（用于 discover 响应）。 */
    public static final String SERVER_INFO = "io.modelcontextprotocol/serverInfo";
    /** {@code _meta.io.modelcontextprotocol/logLevel}：日志级别调整指令。 */
    public static final String LOG_LEVEL = "io.modelcontextprotocol/logLevel";
    /** {@code _meta.io.modelcontextprotocol/subscriptionId}：订阅标识。 */
    public static final String SUBSCRIPTION_ID = "io.modelcontextprotocol/subscriptionId";
    /** {@code _meta.progressToken}：JSON-RPC 2.0 通用进度通知令牌，不在 MCP 私有命名空间。 */
    public static final String PROGRESS_TOKEN = "progressToken";

    private McpMetaKeys() {
    }
}
