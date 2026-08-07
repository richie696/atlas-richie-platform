package cn.richie696.component.oauth.core.spi;

import cn.richie696.component.oauth.core.model.ClientConfig;

import java.util.List;
import java.util.Map;

/** Access Token 签名和验证端口，默认实现为兼容用 HMAC，AS 可注入 RSA/EC 实现。 */
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
