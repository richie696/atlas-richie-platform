package cn.richie696.component.oauth.oidc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 经过签名与协议校验后的 OIDC ID Token 关键 Claims 容器。
 *
 * <p>处于 {@link OidcIdTokenVerifier} 与 OAuth Service 业务层之间：上游从 ID Token 的 JWT payload
 * 抽取并校验核心字段，下游供业务侧读取 sub/aud/auth_time/nonce/at_hash 等用于会话绑定、
 * nonce 比对与 at_hash 一致性检查的字段。它只承载经过验证后的视图，不接触解码与验签逻辑。
 *
 * <p>解决"ID Token payload 是裸 Map，业务层读取字段时缺乏类型约束、容易拼错 claim 名"
 * 的易错场景，把 OIDC 必读 Claims 固化成 record 并保证返回的列表/Maps 不可变，降低
 * 上层误用造成的安全风险。
 *
 * @author richie696
 * @since 2026-08-07
 */
public record OidcIdTokenClaims(
        String issuer,
        String subject,
        List<String> audience,
        long expiresAt,
        long issuedAt,
        Long authenticationTime,
        String nonce,
        String accessTokenHash,
        Map<String, Object> claims
) {
    public OidcIdTokenClaims {
        audience = audience == null ? List.of() : List.copyOf(audience);
        claims = claims == null ? Collections.emptyMap() : Map.copyOf(claims);
    }
}
