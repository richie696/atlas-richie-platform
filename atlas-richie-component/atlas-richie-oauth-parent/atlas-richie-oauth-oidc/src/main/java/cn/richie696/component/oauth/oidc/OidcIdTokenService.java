package cn.richie696.component.oauth.oidc;

import cn.richie696.component.oauth.oidc.config.OidcProperties;
import cn.richie696.contract.exception.BusinessException;

/** ID Token 领域外观，确保只有 OIDC 请求才能产生 ID Token。 */
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
