package cn.richie696.component.mcp.protocol.discovery;

import cn.richie696.component.mcp.protocol.model.McpImplementationInfo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@code server/discover} 的协议无关内部结果。
 *
 * <p>为什么需要协议无关模型：业务侧（如服务注册中心、客户端缓存策略）只需关心"对端支持
 * 哪些协议版本 / 有什么能力 / 我可以缓存多久"，不需要关心这些字段在 JSON 上的嵌套关系。
 * {@link McpDiscoverResult} 提供这一扁平化视图，{@link McpDiscoveryCodec} 负责其与
 * 线格式之间的转换。</p>
 *
 * <p>关键设计：
 * <ul>
 *   <li>{@code extensions} 字段透传未识别的扩展字段，方便协议演进。</li>
 *   <li>{@code supportedVersions} 在构造期去重并校验非空，{@code ttlMs} 强制非负。</li>
 * </ul>
 * </p>
 *
 * @param supportedVersions 对端支持的协议版本列表（去重后），至少一个非空
 * @param capabilities      对端能力声明
 * @param serverInfo        对端实现身份，可为 {@code null}
 * @param instructions      可读的客户端使用说明，可为 {@code null}
 * @param ttlMs             缓存 TTL 毫秒数，非负
 * @param cacheScope        缓存作用域
 * @param extensions        透传的扩展字段
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpDiscoverResult(
        List<String> supportedVersions,
        Map<String, Object> capabilities,
        McpImplementationInfo serverInfo,
        String instructions,
        long ttlMs,
        McpCacheScope cacheScope,
        Map<String, Object> extensions) {

    /**
     * 紧凑构造器：必填校验 + 集合归一。
     *
     * @throws IllegalArgumentException 当版本列表为空、含空白或 {@code ttlMs} 为负时
     */
    public McpDiscoverResult {
        Objects.requireNonNull(supportedVersions, "supportedVersions");
        // 用 LinkedHashSet 去重同时保留入参顺序
        supportedVersions = List.copyOf(new LinkedHashSet<>(supportedVersions));
        if (supportedVersions.isEmpty() || supportedVersions.stream().anyMatch(version -> version == null
                || version.isBlank())) {
            throw new IllegalArgumentException("supportedVersions must contain at least one non-blank version");
        }
        capabilities = capabilities == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(capabilities));
        if (ttlMs < 0) {
            throw new IllegalArgumentException("ttlMs must be a non-negative integer");
        }
        Objects.requireNonNull(cacheScope, "cacheScope");
        extensions = extensions == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(extensions));
    }
}
