package cn.richie696.component.oauth.resource;

import cn.richie696.component.oauth.cache.OAuthCache;
import cn.richie696.component.oauth.contract.model.OAuthIntrospectionResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** introspection 短缓存；只缓存哈希 Key，不把原始 Token 写入 Redis Key。 */
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
