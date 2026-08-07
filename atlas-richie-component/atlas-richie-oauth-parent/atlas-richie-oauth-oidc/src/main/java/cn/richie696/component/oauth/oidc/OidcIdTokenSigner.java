package cn.richie696.component.oauth.oidc;

/** OIDC ID Token 签名 SPI；生产环境由 OAuth Service 注入 RSA/EC 实现。 */
public interface OidcIdTokenSigner {

    String sign(OidcIdTokenRequest request);
}
