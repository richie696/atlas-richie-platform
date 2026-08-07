package cn.richie696.component.oauth.oidc;

import java.net.URI;

/** Backchannel Logout HTTP 投递边界；HTTP 客户端、重试和熔断由 OAuth Service 注入。 */
@FunctionalInterface
public interface OidcBackchannelLogoutNotifier {

    void notify(URI endpoint, String logoutToken);
}
