package cn.richie696.component.oauth.oidc;

import cn.richie696.component.oauth.oidc.config.OidcProperties;
import cn.richie696.contract.exception.BusinessException;

/**
 * OIDC ID Token 的领域外观服务，统一拦截"非法 OIDC 请求试图签发 ID Token"。
 *
 * <p>处于 OAuth Service 与 {@link OidcIdTokenSigner} 之间：上游接
 * {@link OidcIdTokenRequest}（来自业务侧），下游依赖配置 {@code OidcProperties} 与
 * 注入的 signer 完成签发。它是 OIDC 协议侧的最末一道门：scope 缺失 openid、subject 或
 * clientId 为空、未配 nonce 等违反规范的请求在这里被直接拒绝，绝不流入 signer。
 *
 * <p>解决"OAuth 请求与 OIDC 请求共用一条 token 签发链路、可能误签出 ID Token"的协议
 * 越权问题，让 ID Token 只能由合法的 OIDC 交互产生，从而保证下游 RP 在验证 ID Token
 * 时所依赖的语义不会因为 AS 配置不当而破裂。
 *
 * @author richie696
 * @since 2026-08-07
 */
public final class OidcIdTokenService {

    private final OidcProperties properties;
    private final OidcIdTokenSigner signer;

    public OidcIdTokenService(OidcProperties properties, OidcIdTokenSigner signer) {
        this.properties = properties;
        this.signer = signer;
    }

    public String issue(OidcIdTokenRequest request) {
        if (request == null || !request.hasScope(OidcConstants.OPENID_SCOPE)) {
            throw new BusinessException("invalid_scope", "只有包含 openid scope 的请求才能签发 ID Token");
        }
        if (blank(request.subject()) || blank(request.clientId())) {
            throw new BusinessException("invalid_request", "ID Token 必须包含 subject 和 client_id");
        }
        if (properties.isRequireNonce() && blank(request.nonce())) {
            throw new BusinessException("invalid_request", "ID Token 必须绑定 nonce");
        }
        return signer.sign(request);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
