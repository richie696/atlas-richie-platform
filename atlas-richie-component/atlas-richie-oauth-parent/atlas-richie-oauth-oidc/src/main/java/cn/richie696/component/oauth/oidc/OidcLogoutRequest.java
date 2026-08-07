package cn.richie696.component.oauth.oidc;

/** RP-Initiated Logout 请求模型；会话清理由 OAuth Service 注入的 Session SPI 完成。 */
public record OidcLogoutRequest(
        String idTokenHint,
        String logoutHint,
        String clientId,
        String postLogoutRedirectUri,
        String state
) {
}
