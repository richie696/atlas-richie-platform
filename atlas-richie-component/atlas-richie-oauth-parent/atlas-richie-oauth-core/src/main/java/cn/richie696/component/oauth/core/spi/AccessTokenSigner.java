package cn.richie696.component.oauth.core.spi;

import cn.richie696.component.oauth.core.model.ClientConfig;

import java.util.List;
import java.util.Map;

/**
 * Access Token 签名与验证的端口。
 * <p>
 * 抽象出"用什么算法(对称/非对称)把 JWT 签出来,以及如何验签解析回 Claims"这一动作;默认实现为
 * 兼容用 HMAC(见 {@link cn.richie696.component.oauth.core.support.HmacAccessTokenSigner}),
 * 生产 Authorization Server 应注入 RSA/EC 实现(见
 * {@link cn.richie696.component.oauth.core.support.RsaAccessTokenSigner}),以满足分布式 RS256
 * 校验与 JWKS 发布。
 * </p>
 * <p>
 * 处于 oauth-core 的签名能力位置:由 {@link cn.richie696.component.oauth.core.TokenEndpoint} 与
 * AuthorizationCodeGrant 调用;Resource Server 通过
 * JWKS 端点完成验签,不直接持有该端口。
 * </p>
 * <p>
 * 解决的问题:让 access token 的签名算法与密钥管理(JWK 轮换、对称/非对称切换)成为可替换的 SPI,
 * 既能支持内网共享密钥的轻量场景,也能平滑过渡到生产级非对称签名。
 * </p>
 *
 * @author richie696
 * @since 2026-08-07
 */
public interface AccessTokenSigner {

    String sign(String clientId, ClientConfig client, List<String> scopes, String resource);

    /** 带受信任扩展声明的签发入口，旧签名器默认忽略扩展声明。 */
    default String sign(String clientId, ClientConfig client, List<String> scopes,
                        String resource, Map<String, Object> additionalClaims) {
        return sign(clientId, client, scopes, resource);
    }

    /** 带资源主体的签发入口，Authorization Code/Device Flow 使用用户 subject。 */
    default String sign(String clientId, ClientConfig client, List<String> scopes,
                        String resource, String subject, Map<String, Object> additionalClaims) {
        return sign(clientId, client, scopes, resource, additionalClaims);
    }

    AccessTokenClaims verify(String accessToken);

    record AccessTokenClaims(String clientId, String subject, String issuer,
                             String audience, String tokenId, long expiresAt,
                             List<String> scopes) {
    }
}
