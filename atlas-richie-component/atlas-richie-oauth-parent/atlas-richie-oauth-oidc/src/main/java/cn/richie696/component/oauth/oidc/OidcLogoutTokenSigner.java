package cn.richie696.component.oauth.oidc;

/** Backchannel Logout Token 签名 SPI；生产环境由 OAuth Service 注入密钥托管实现。 */
@FunctionalInterface
public interface OidcLogoutTokenSigner {

    String sign(OidcLogoutTokenRequest request);
}
