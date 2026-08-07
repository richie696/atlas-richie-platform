package cn.richie696.component.oauth.oidc;

import cn.richie696.component.oauth.contract.model.OAuthAuthorizationRequest;
import cn.richie696.component.oauth.oidc.config.OidcProperties;
import cn.richie696.contract.exception.BusinessException;

/**
 * OIDC Authorization Code 请求前置校验器，不负责登录、MFA 或用户同意。
 *
 * <p>处于 OIDC 请求链的最前沿：上游是 OAuth Service 的 authorization_endpoint 入口，
 * 下游是 OAuth 核心的 {@code OAuthAuthorizationRequest} 流转。它只校验协议形态
 * （openid scope、response_type/response_mode、nonce 等），把业务层面的身份判定全部留给
 * 服务侧。
 *
 * <p>解决"任何 scope/response_type 都可能被当作 OIDC 请求处理"导致的协议降级与重放风险，
 * 让 AS 在不耦合业务的前提下保证只有合法的 OIDC 交互才能进入后续 ID Token 签发流程。
 *
 * @author richie696
 * @since 2026-08-07
 */
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
