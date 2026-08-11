package cn.richie696.component.mcp.security.oauth;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps the internal PRM model to RFC 9728 JSON field names.
 *
 * <p>负责把 {@link McpProtectedResourceMetadata} 序列化为符合 RFC 9728 §2 字段名的
 * JSON-friendly {@link Map}。当前仅提供 {@code encode} 方向：解析侧由
 * {@link McpOAuthMetadataClient#fetchProtectedResourceMetadata(java.net.URI)} 直接处理。
 * 这种"分两条路"的设计避免了引入 Jackson 注解带来的双向耦合，让 record 保持纯净。</p>
 *
 * <p>典型使用场景：MCP 资源服务器侧需要把内部 PRM 模型对外发布（{@code /.well-known/...}
 * 端点）或回传给上游网关做策略决策。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpProtectedResourceMetadataCodec {
    private McpProtectedResourceMetadataCodec() {
    }

    /**
     * 把内部 PRM 模型编码为 RFC 9728 字段名的不可变 Map。
     *
     * <p>编码规则：</p>
     * <ul>
     *   <li>必填字段 {@code resource} 始终写入并转为字符串；</li>
     *   <li>非空集合字段（{@code authorization_servers}、{@code scopes_supported}）按
     *       RFC 9728 命名（snake_case）写入，空集合字段被省略以避免无意义空数组；</li>
     *   <li>扩展字段（{@code extensions}）原样合并，保留 AS 自定义键名。</li>
     * </ul>
     *
     * <p>为何用 {@link LinkedHashMap} + {@link Map#copyOf}：保持字段插入顺序便于日志与
     单元测试断言；不可变封装防止调用方在拿到结果后意外修改污染后续发布流程。</p>
     *
     * @param metadata 待编码的 PRM 模型（必填）
     * @return 字段已映射为 RFC 9728 命名、不可变的 JSON-ready Map
     * @throws NullPointerException 当 metadata 为 null 时
     */
    public static Map<String, Object> encode(McpProtectedResourceMetadata metadata) {
        java.util.Objects.requireNonNull(metadata, "metadata");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resource", metadata.resource().toString());
        if (!metadata.authorizationServers().isEmpty()) {
            result.put("authorization_servers", metadata.authorizationServers().stream()
                    .map(java.net.URI::toString)
                    .toList());
        }
        if (!metadata.scopesSupported().isEmpty()) {
            result.put("scopes_supported", metadata.scopesSupported());
        }
        result.putAll(metadata.extensions());
        return Map.copyOf(result);
    }
}
