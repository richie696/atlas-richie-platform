package cn.richie696.component.mcp.protocol;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 统一处理 Server 与 Client 的版本选择，顺序代表本组件偏好。
 *
 * <p>为什么需要协商器：MCP 协议允许客户端在 {@code initialize} 报文中列出多个
 * 支持的协议版本，服务端从中选择其一。但本组件作为公共协议层，希望把"本组件支持哪些版本"
 * 这一信息集中在一处，并明确给出"本组件更偏好哪个版本"的顺序——这就是
 * {@code supportedVersions} 的入参顺序。</p>
 *
 * <p>关键设计：
 * <ul>
 *   <li>支持列表用 {@link LinkedHashSet} 去重同时保留顺序，确保偏好表达稳定。</li>
 *   <li>协商时按"本组件偏好"为外层、对端支持集合为过滤条件，命中即返回——保证选择
 *       结果永远落在本组件最熟悉/最稳定的协议路径上。</li>
 *   <li>协商失败抛 {@link McpProtocolException}，错误码 {@code -32022}（unified
 *       MCP_UNSUPPORTED_PROTOCOL_VERSION），并把本端支持版本附在 {@code data} 中。</li>
 * </ul>
 * </p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpProtocolNegotiator {
    private final List<String> supportedVersions;

    /**
     * 使用默认支持版本构造协商器。
     *
     * <p>默认支持版本来自 {@link McpProtocolVersions#SUPPORTED}。</p>
     */
    public McpProtocolNegotiator() {
        this(McpProtocolVersions.SUPPORTED);
    }

    /**
     * 使用自定义支持版本构造协商器。
     *
     * @param supportedVersions 本组件支持的协议版本列表，顺序即为偏好顺序；至少包含一项
     * @throws IllegalArgumentException 当列表为空时
     */
    public McpProtocolNegotiator(List<String> supportedVersions) {
        Objects.requireNonNull(supportedVersions, "supportedVersions");
        // 用 LinkedHashSet 去重的同时保留顺序，确保偏好表达稳定
        this.supportedVersions = List.copyOf(new LinkedHashSet<>(supportedVersions));
        if (this.supportedVersions.isEmpty()) {
            throw new IllegalArgumentException("At least one protocol version is required");
        }
    }

    /**
     * 与对端协商出一个共同支持的协议版本。
     *
     * <p>协商策略：遍历本组件偏好列表，命中对端支持的第一个版本即返回；若对端支持的
     * 全部不在本端偏好中，则抛出 {@link McpProtocolException}。</p>
     *
     * @param peerVersions 对端支持的协议版本集合
     * @return 协商成功后的协议版本
     * @throws McpProtocolException 当没有共同支持的版本时
     */
    public String negotiate(List<String> peerVersions) {
        Set<String> offered = new LinkedHashSet<>(Objects.requireNonNull(peerVersions, "peerVersions"));
        return supportedVersions.stream()
                .filter(offered::contains)
                .findFirst()
                .orElseThrow(() -> new McpProtocolException(
                        "MCP_UNSUPPORTED_PROTOCOL_VERSION",
                        -32022,
                        "No mutually supported MCP protocol version",
                        Map.of("supported", supportedVersions)));
    }

    /**
     * 校验某个版本是否在本端支持列表中（用于单边校验场景，如服务端拒绝老旧客户端）。
     *
     * @param version 待校验的协议版本
     * @throws McpProtocolException 当版本不在支持列表中时
     */
    public void requireSupported(String version) {
        if (!supportedVersions.contains(version)) {
            throw new McpProtocolException(
                    "MCP_UNSUPPORTED_PROTOCOL_VERSION",
                    -32022,
                    "Unsupported MCP protocol version: " + version,
                    Map.of("supported", supportedVersions, "requested", version));
        }
    }

    /**
     * 返回本组件支持且按偏好排序的版本列表。
     *
     * @return 不可变的有序版本列表
     */
    public List<String> supportedVersions() {
        return supportedVersions;
    }
}
