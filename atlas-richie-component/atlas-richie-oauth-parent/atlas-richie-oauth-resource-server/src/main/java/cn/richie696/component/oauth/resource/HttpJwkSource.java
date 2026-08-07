package cn.richie696.component.oauth.resource;

import cn.richie696.component.oauth.cache.OAuthCache;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 从远程 JWKS endpoint 拉取并缓存 RSA 公钥的 {@link JwkSource} 默认实现。
 *
 * <p>处于 {@link DefaultJwtTokenVerifier} 与 AS 的 {@code /.well-known/jwks.json} 端点
 * 之间：上游按 {@code kid} 查询公钥，下游借助 {@code OAuthCache} 完成跨实例的 JWKS
 * 文档缓存，并在请求的 {@code kid} 不存在时强制刷新一次以容忍密钥轮换。本地进程内还
 * 保留一份 {@code kid → RSAPublicKey} 副本用于快速命中，HTTP 仅在缓存 miss 时触发。
 *
 * <p>解决"Resource Server 每次验签都重新拉 JWKS 既浪费又对 AS 构成 DDoS"和"密钥
 * 轮换期老 token 验签失败"这对矛盾：通过分布式缓存 + 单次强制刷新策略，既把网络开销
 * 压到最低，又能在不重启 Resource Server 的前提下适应 AS 的密钥轮换。
 *
 * @author richie696
 * @since 2026-08-07
 */
public class HttpJwkSource implements JwkSource {

    private final URI jwksUri;
    private final OAuthCache cache;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Duration timeout;
    private final long cacheTtlMillis;
    private final Map<String, RSAPublicKey> localKeys = new ConcurrentHashMap<>();
    private volatile String loadedJson;

    public HttpJwkSource(URI jwksUri, OAuthCache cache, Duration timeout, Duration cacheTtl) {
        this(jwksUri, cache, timeout, cacheTtl,
                HttpClient.newBuilder().connectTimeout(timeout).build());
    }

    HttpJwkSource(URI jwksUri, OAuthCache cache, Duration timeout, Duration cacheTtl,
                  HttpClient httpClient) {
        this.jwksUri = jwksUri;
        this.cache = cache;
        this.httpClient = httpClient;
        this.timeout = timeout;
        this.cacheTtlMillis = cacheTtl.toMillis();
    }

    @Override
    public RSAPublicKey find(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            return null;
        }
        RSAPublicKey local = localKeys.get(keyId);
        if (local != null) {
            return local;
        }

        String cached = cache.get(cacheKey(), String.class);
        if (cached == null) {
            cached = fetch();
            cache.put(cacheKey(), cached, cacheTtlMillis);
        }
        parseKeysIfChanged(cached);
        RSAPublicKey refreshed = localKeys.get(keyId);
        if (refreshed == null) {
            cache.remove(cacheKey());
            localKeys.clear();
            String refreshedJson = fetch();
            cache.put(cacheKey(), refreshedJson, cacheTtlMillis);
            parseKeysIfChanged(refreshedJson);
            refreshed = localKeys.get(keyId);
        }
        return refreshed;
    }

    private String fetch() {
        try {
            HttpRequest request = HttpRequest.newBuilder(jwksUri)
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new ResourceServerException("JWKS endpoint 返回 HTTP " + response.statusCode());
            }
            return response.body();
        } catch (Exception e) {
            throw new ResourceServerException("获取 JWKS 失败: " + jwksUri, e);
        }
    }

    private synchronized void parseKeysIfChanged(String json) {
        if (json == null || json.equals(loadedJson)) {
            return;
        }
        localKeys.clear();
        parseKeys(json);
        loadedJson = json;
    }

    private void parseKeys(String json) {
        try {
            JsonNode keys = objectMapper.readTree(json).path("keys");
            if (!keys.isArray()) {
                throw new ResourceServerException("JWKS 响应缺少 keys 数组");
            }
            for (JsonNode key : keys) {
                if (!"RSA".equals(key.path("kty").asText())
                        || key.path("kid").isMissingNode()
                        || key.path("kid").asText().isBlank()
                        || key.path("n").asText().isBlank()
                        || key.path("e").asText().isBlank()) {
                    continue;
                }
                String kid = key.path("kid").asText();
                byte[] modulus = Base64.getUrlDecoder().decode(key.path("n").asText());
                byte[] exponent = Base64.getUrlDecoder().decode(key.path("e").asText());
                RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                        .generatePublic(new RSAPublicKeySpec(new BigInteger(1, modulus), new BigInteger(1, exponent)));
                localKeys.put(kid, publicKey);
            }
        } catch (ResourceServerException e) {
            throw e;
        } catch (Exception e) {
            throw new ResourceServerException("解析 JWKS 失败", e);
        }
    }

    private String cacheKey() {
        return "oauth:resource:jwks:" + jwksUri;
    }
}
