package cn.richie696.component.oauth.oidc;

import cn.richie696.component.oauth.oidc.config.OidcProperties;
import cn.richie696.contract.exception.BusinessException;

/**
 * OIDC 授权响应的领域构造器，封装 query/form_post/hybrid 三种 response_type 的字段拼装规则。
 *
 * <p>处于 OAuth Service 与协议层之间：上游接收 OAuth 核心产出
 * 的 {@code OAuthAuthorizationRequest} 与业务侧颁发的 code/token，下游产出
 * {@link OidcAuthorizationResponse} 给 HTTP 适配层做序列化。它只负责"响应里应该出现哪些字段"，
 * 不负责 302 重定向、form_post HTML 渲染或前端跳转这些交付层细节。
 *
 * <p>解决"AS 把 HTTP 重定向逻辑散落在多个 Controller 里"导致的 response_mode 行为不一致问题，
 * 把所有 response_type 与 response_mode 的字段缺失校验收敛到一处，避免漏返 code/id_token 的
 * 半成品响应走出 AS。
 *
 * @author richie696
 * @since 2026-08-07
 */
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
