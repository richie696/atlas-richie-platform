package cn.richie696.component.mcp.security.oauth;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * RFC 9728 Protected Resource Metadata 的内部归一化模型。
 *
 * <p>该 record 把 RFC 9728 §2 的核心字段收敛为四个 record component：</p>
 *
 * <ul>
 *   <li>{@code resource}：受保护资源标识（RFC 9728 强制字段）；</li>
 *   <li>{@code authorizationServers}：推荐 AS 列表（RFC 9728 推荐字段，客户端应优先选用）；</li>
 *   <li>{@code scopesSupported}：资源支持的 scope 列表（可选）；</li>
 *   <li>{@code extensions}：原始 JSON 中的扩展字段（如 {@code bearer_methods_supported}、
 *       {@code resource_documentation} 等），透传给上层避免 record 字段爆炸。</li>
 * </ul>
 *
 * <p>为何把原始 JSON 作为 {@code extensions} 透传：RFC 9728 明确允许 AS 自定义扩展字段，
 * 把这些字段一并保留在 record 内能让后续序列化（如 {@link McpProtectedResourceMetadataCodec}）
 * 保持"输出=输入"语义，避免出现"读得到但写不出"的协议字段。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpProtectedResourceMetadata(
        URI resource,
        List<URI> authorizationServers,
        List<String> scopesSupported,
        Map<String, Object> extensions) {

    /**
     * 紧凑构造器：做必填校验与不可变拷贝。
     *
     * <p>{@code resource} 强制非空：RFC 9728 §2.2 明确其为必填字段，且后续 token 请求需要
     把该值作为 {@code resource} 参数回传给 AS；空值会让授权上下文丢失。所有集合字段统一做
     {@link List#copyOf}/{@link Map#copyOf} 不可变拷贝，使 PRM 模型可被多线程缓存与共享。</p>
     *
     * @param resource 受保护资源 URI（必填）
     * @param authorizationServers 推荐 AS URI 列表（不可变拷贝）
     * @param scopesSupported 资源支持的 scope 列表（不可变拷贝）
     * @param extensions 扩展字段映射（不可变拷贝），保留全部原始 JSON 字段
     * @throws NullPointerException 当 resource 为 null 时
     */
    public McpProtectedResourceMetadata {
        resource = java.util.Objects.requireNonNull(resource, "resource");
        authorizationServers = authorizationServers == null ? List.of() : List.copyOf(authorizationServers);
        scopesSupported = scopesSupported == null ? List.of() : List.copyOf(scopesSupported);
        extensions = extensions == null ? Map.of() : Map.copyOf(extensions);
    }
}
