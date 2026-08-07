package cn.richie696.component.oauth.oidc;

import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.contract.exception.BusinessException;

/** 校验 RP-Initiated Logout 的客户端和回调地址，避免开放重定向。 */
public final class OidcLogoutValidator {

    public OidcLogoutRequest validate(OidcLogoutRequest request, ClientConfig client) {
        if (request == null) {
            throw error("invalid_request", "Logout 请求不能为空");
        }
        if (blank(request.idTokenHint()) && blank(request.logoutHint()) && blank(request.clientId())) {
            throw error("invalid_request", "Logout 请求至少需要 id_token_hint、logout_hint 或 client_id");
        }
        if (request.postLogoutRedirectUri() != null) {
            if (client == null || client.getRedirectUris() == null
                    || !client.getRedirectUris().contains(request.postLogoutRedirectUri())) {
                throw error("invalid_request", "post_logout_redirect_uri 未注册");
            }
        }
        return request;
    }

    private BusinessException error(String code, String message) {
        return new BusinessException(code, message);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
