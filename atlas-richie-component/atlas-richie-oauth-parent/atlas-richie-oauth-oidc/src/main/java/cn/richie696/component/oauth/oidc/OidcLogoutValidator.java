package cn.richie696.component.oauth.oidc;

import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.contract.exception.BusinessException;

/**
 * RP-Initiated Logout 请求的前置校验器，重点保护 post_logout_redirect_uri 不被滥用于
 * 开放重定向。
 *
 * <p>处于 OAuth Service 的 end_session_endpoint 与协议执行之间：上游接
 * {@link OidcLogoutRequest} 与客户端注册信息，下游产出可继续执行的合法请求。它要求
 * 至少存在 id_token_hint / logout_hint / clientId 之一，并把 post_logout_redirect_uri
 * 严格限定在 {@code ClientConfig.redirectUris} 注册集合内，不做"模糊匹配"或"同源放行"。
 *
 * <p>解决"AS 把 post_logout_redirect_uri 当成普通回调校验、容易被诱导到外部域名"的
 * 钓鱼/重定向攻击风险，把注销场景下的回跳地址白名单收敛到一处，避免每个 RP 接入方
 * 都重新实现一套安全规则。
 *
 * @author richie696
 * @since 2026-08-07
 */
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
