package cn.richie696.component.mcp.protocol;

import java.util.List;

/**
 * 组件已验证的 MCP 协议版本。
 *
 * <p>集中维护两个语义：
 * <ul>
 *   <li>各版本号常量，避免散落的魔法字符串。</li>
 *   <li>{@link #SUPPORTED} 列表声明本组件支持的版本，顺序代表偏好——更靠前的版本
 *       会被 {@link McpProtocolNegotiator} 优先选择。</li>
 * </ul>
 * </p>
 *
 * <p>新增版本时需同时更新 {@link #SUPPORTED} 与对应方言实现，并保证
 * {@link McpSchemaSnapshot#load(String)} 中资源可用。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpProtocolVersions {
    /** 2026-07-28 协议版本号（无状态协议时代的首个稳定版本）。 */
    public static final String V_2026_07_28 = "2026-07-28";
    /** 2025-11-25 协议版本号（会话/initialize 协议时代的最后一个稳定版本）。 */
    public static final String V_2025_11_25 = "2025-11-25";
    /** 组件支持的协议版本列表，顺序即协商偏好。 */
    public static final List<String> SUPPORTED = List.of(V_2026_07_28, V_2025_11_25);

    private McpProtocolVersions() {
    }
}
