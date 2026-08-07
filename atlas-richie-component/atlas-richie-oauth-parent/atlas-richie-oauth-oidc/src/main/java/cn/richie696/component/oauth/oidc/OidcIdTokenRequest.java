package cn.richie696.component.oauth.oidc;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * OIDC ID Token 签发的领域请求，由 OAuth Service 在完成用户认证与同意后构造并下发。
 *
 * <p>处于 OAuth Service 与 {@link OidcIdTokenService} 之间：上游是 OAuth Service 已收集到
 * 的主体身份（sub）、客户端身份（clientId）、用户授权范围（scopes）以及可选 nonce，
 * 下游被 ID Token 域对象用来构造 ID Token Claims。它不直接绑定登录、用户表或
 * Session，是协议层与业务侧之间的纯数据载体。
 *
 * <p>解决"业务层与 ID Token 签发之间耦合过深、协议字段散落在请求对象各处"导致的协议回归
 * 成本，把 OIDC 协议规定的 sub/aud/nonce/auth_time/at_hash 等字段集中到一个不可变
 * record，方便业务侧按需填充、协议侧按需读取。
 *
 * @author richie696
 * @since 2026-08-07
 */
public record OidcIdTokenRequest(
        String subject,
        String clientId,
        String nonce,
        Instant authenticationTime,
        Collection<String> scopes,
        Map<String, Object> claims,
        String accessToken
) {
    public OidcIdTokenRequest {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        claims = claims == null ? Map.of() : Map.copyOf(claims);
    }

    public boolean hasScope(String scope) {
        return scope != null && scopes.contains(scope);
    }
}
