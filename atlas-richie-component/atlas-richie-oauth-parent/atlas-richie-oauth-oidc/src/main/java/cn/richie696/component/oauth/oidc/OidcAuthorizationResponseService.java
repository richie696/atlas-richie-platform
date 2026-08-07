package cn.richie696.component.oauth.oidc;

import cn.richie696.component.oauth.oidc.config.OidcProperties;
import cn.richie696.contract.exception.BusinessException;

/** 构造 OIDC query/form_post/hybrid 响应，不负责 HTTP 重定向或 HTML 渲染。 */
public final class OidcAuthorizationResponseService {

    private final OidcProperties properties;

    public OidcAuthorizationResponseService(OidcProperties properties) {
        this.properties = properties;
    }

    public OidcAuthorizationResponse success(String responseType, String responseMode,
                                              String code, String idToken,
                                              String accessToken, String state) {
        validate(responseType, responseMode);
        if (requires(responseType, "code") && blank(code)) {
            throw error("invalid_request", "响应缺少 authorization code");
        }
        if (requires(responseType, "id_token") && blank(idToken)) {
            throw error("invalid_request", "响应缺少 id_token");
        }
        if (requires(responseType, "token") && blank(accessToken)) {
            throw error("invalid_request", "响应缺少 access_token");
        }
        return new OidcAuthorizationResponse(code, idToken, accessToken, state,
                effectiveMode(responseMode), null, null);
    }

    public OidcAuthorizationResponse failure(String responseMode, String state,
                                              String error, String description) {
        validateMode(responseMode);
        if (blank(error)) {
            throw error("invalid_request", "OIDC 错误响应缺少 error");
        }
        return new OidcAuthorizationResponse(null, null, null, state,
                effectiveMode(responseMode), error, description);
    }

    private void validate(String responseType, String responseMode) {
        if (blank(responseType) || !properties.getResponseTypesSupported().contains(responseType)) {
            throw error("unsupported_response_type", "response_type 未被 Provider 支持");
        }
        validateMode(responseMode);
    }

    private void validateMode(String responseMode) {
        if (!blank(responseMode) && !properties.getResponseModesSupported().contains(responseMode)) {
            throw error("invalid_request", "response_mode 未被 Provider 支持");
        }
    }

    private String effectiveMode(String responseMode) {
        return blank(responseMode) ? "query" : responseMode;
    }

    private boolean requires(String responseType, String value) {
        return java.util.Arrays.asList(responseType.split("\\s+")).contains(value);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private BusinessException error(String code, String message) {
        return new BusinessException(code, message);
    }
}
