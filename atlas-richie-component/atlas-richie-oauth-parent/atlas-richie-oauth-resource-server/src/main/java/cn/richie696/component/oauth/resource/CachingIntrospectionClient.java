package cn.richie696.component.oauth.resource;

import cn.richie696.component.oauth.cache.OAuthCache;
import cn.richie696.component.oauth.contract.model.OAuthIntrospectionResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * introspection 响应的短缓存装饰器，用 SHA-256 哈希作为缓存 Key，绝不让原始 token 进入缓存介质。
 *
 * <p>处于 {@link ResourceServerAuthenticator} 与真正发起 introspection 请求的下
 * （如 {@code StandardOAuthIntrospectionClient}）之间：上游调用方传入明文 token，
 * 本装饰器先查缓存、命中即返回，未命中才委托 delegate 真正调用 AS 并按配置的 TTL 写入
 * 缓存。它只承担"读穿 / 写回"职责，不感知 HTTP 协议细节与 OAuth 错误码。
 *
 * <p>解决"introspection 端点每个请求都被 AS 拒绝服务且原始 token 容易泄漏到 Redis Key
 * 监控里"的双重痛点：缓存降低 AS 压力、哈希 Key 保证原始 token 不会以明文形式落到分布式缓存
 * * 与运维监控中；TTL 故意保持短，确保撤销语义不会因为缓存而长期失效。
 *
 * @author richie696
 * @since 2026-08-07
 */
public class CachingIntrospectionClient implements IntrospectionClient {

    private final OAuthCache cache;
    private final IntrospectionClient delegate;
    private final long ttlMillis;

    public CachingIntrospectionClient(OAuthCache cache, IntrospectionClient delegate, long ttlMillis) {
        this.cache = cache;
        this.delegate = delegate;
        this.ttlMillis = ttlMillis;
    }

    @Override
    public OAuthIntrospectionResponse introspect(String token) {
        if (token == null || token.isBlank()) {
            throw new ResourceServerException("introspection token 不能为空");
        }
        String key = "oauth:resource:introspection:" + hash(token);
        OAuthIntrospectionResponse cached = cache.get(key, OAuthIntrospectionResponse.class);
        if (cached != null) {
            return cached;
        }
        OAuthIntrospectionResponse result = delegate.introspect(token);
        if (result != null) {
            cache.put(key, result, ttlMillis);
        }
        return result;
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
