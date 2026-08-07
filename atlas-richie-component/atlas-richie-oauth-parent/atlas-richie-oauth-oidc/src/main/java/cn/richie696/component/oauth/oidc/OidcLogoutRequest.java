package cn.richie696.component.oauth.oidc;

/**
 * RP-Initiated Logout 请求的领域模型，承载 id_token_hint、logout_hint、clientId、
 * post_logout_redirect_uri、state 等可选字段。
 *
 * <p>处于 OAuth Service 的 end_session_endpoint 与 {@link OidcLogoutValidator} 之间：
 * 上游由 OAuth Service 把浏览器重定向参数解析成本 record，下游供协议校验与回跳地址
 * 校验使用。会话清理本身（即"在 AS 这一侧把用户登出"）由 OAuth Service 注入的 Session
 * SPI 完成，本 record 不知道也不关心 Session 存放在哪里。
 *
 * <p>解决"RP-Initiated Logout 字段散落在 Controller 方法签名里、回跳地址校验逻辑分散"
 * 的一致性问题，让 AS 能用统一的数据结构承接不同 RP 提交的注销请求，并方便扩展更多
 * 提示字段（logout_hint、ui_locales 等）而不破坏既有方法签名。
 *
 * @author richie696
 * @since 2026-08-07
 */
public record OidcLogoutRequest(
        String idTokenHint,
        String logoutHint,
        String clientId,
        String postLogoutRedirectUri,
        String state
) {
}
