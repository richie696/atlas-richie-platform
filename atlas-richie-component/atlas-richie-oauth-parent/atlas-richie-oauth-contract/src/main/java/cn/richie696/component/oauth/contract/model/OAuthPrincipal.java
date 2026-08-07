package cn.richie696.component.oauth.contract.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Resource Server 完成 token 校验后向 Gateway 与业务层透出的可信主体模型, 含 subject / clientId / iss / aud / jti / scopes / 未类型化 claims。
 * <p>
 * 处于契约层 Resource Server 与业务服务的边界一环, 是认证结果跨层传递的唯一载体, 上游网关和业务方只面向这一份 record 编写授权判断, 不必再去反序列化 JWT 原始字段或 introspection 响应结构。
 * 解决"Resource Server 内部验证态与业务侧信任态混用, 业务代码被迫透传到 JWT 头"的问题, 把"已验证主体"与"原始 token 串"彻底切开, 让业务侧只持有可序列化的不可变主体对象。
 *
 * @author richie696
 * @since 2026-08-07
 */
public record OAuthPrincipal(
        String subject,
        String clientId,
        String issuer,
        String audience,
        String tokenId,
        List<String> scopes,
        Map<String, Object> claims
) {
    public OAuthPrincipal {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        claims = claims == null ? Collections.emptyMap() : Map.copyOf(claims);
    }

    public boolean hasScope(String scope) {
        return scope != null && scopes.contains(scope);
    }
}
