package cn.richie696.component.oauth.oidc;

import cn.richie696.component.oauth.contract.model.OAuthAuthorizationRequest;
import cn.richie696.component.oauth.oidc.config.OidcProperties;
import cn.richie696.contract.exception.BusinessException;

/** OIDC Authorization Code 请求校验，不负责登录、MFA 或用户同意。 */
public final class OidcAuthorizationRequestValidator {

    private final OidcProperties properties;

    public OidcAuthorizationRequestValidator(OidcProperties properties) {
        this.properties = properties;
    }

    public OAuthAuthorizationRequest validate(OAuthAuthorizationRequest request) {
        if (request == null || request.scopes() == null || !request.scopes().contains(OidcConstants.OPENID_SCOPE)) {
            throw error("invalid_scope", "OIDC 授权请求必须包含 openid scope");
        }
        if (blank(request.responseType()) || !properties.getResponseTypesSupported().contains(request.responseType())) {
            throw error("unsupported_response_type", "OIDC response_type 未被 Provider 支持");
        }
        String responseMode = request.responseMode();
        if (responseMode != null && !responseMode.isBlank()
                && !properties.getResponseModesSupported().contains(responseMode)) {
            throw error("invalid_request", "OIDC response_mode 未被 Provider 支持");
        }
        if (properties.isRequireNonce() && blank(request.nonce())) {
            throw error("invalid_request", "OIDC 授权请求必须包含 nonce");
        }
        return request;
    }

    public boolean isOidcRequest(OAuthAuthorizationRequest request) {
        return request != null && request.scopes() != null
                && request.scopes().contains(OidcConstants.OPENID_SCOPE);
    }

    private BusinessException error(String code, String message) {
        return new BusinessException(code, message);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
