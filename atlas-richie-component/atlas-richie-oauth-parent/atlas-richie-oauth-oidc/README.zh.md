# atlas-richie-oauth-oidc

可选的 OpenID Connect Provider 领域能力模块。它建立在 OAuth Authorization Code + PKCE 之上，但不把用户数据库、登录页面、MFA 或 HTTP Controller 放入公共组件。

## 已提供能力

- `OidcAuthorizationRequestValidator`：`openid` scope、Authorization Code 和 nonce 校验；
- `OidcIdTokenService`：只为 OIDC 请求签发 ID Token；
- `RsaOidcIdTokenSigner`：RS256、`kid`、`iss/sub/aud/exp/iat/auth_time/nonce/at_hash`；
- `OidcIdTokenVerifier`：issuer、audience、nonce 和 RSA 签名验证；
- `OidcUserInfoService`：按 `profile/email/address/phone` scope 过滤 Claims；
- `OidcProviderMetadataService`：生成 Discovery Metadata；
- `OidcLogoutValidator`：RP-Initiated Logout 和 `post_logout_redirect_uri` 校验；
- `OidcBackchannelLogoutService` / `RsaOidcLogoutTokenSigner`：Backchannel Logout Token 生成、客户端投递边界；
- `OidcFrontchannelLogoutService`：生成带 `iss`/`sid` 的 Front-Channel Logout iframe 地址；
- Discovery Metadata：声明 Front-Channel/Backchannel Logout 支持能力；
- `OidcUserInfoProvider`、`OidcIdTokenSigner`：由 OAuth Service 注入具体实现。

## OAuth Service 需要负责的内容

- 登录和 MFA；
- 用户、组织、租户和身份映射；
- 授权同意页面；
- `/authorize`、`/token`、`/userinfo`、`/.well-known/openid-configuration`、`/logout` 等 HTTP 端点；
- RSA 私钥/JWK 生命周期、轮换和安全托管；
- Session 注销和上游 Microsoft/LDAP/AD/OIDC IdP 联邦。
- Backchannel Logout 的 HTTP 重试、熔断、失败补偿和实际 Session 清理。

## 基本使用

```java
var validator = new OidcAuthorizationRequestValidator(properties);
validator.validate(authorizationRequest);

var idToken = new OidcIdTokenService(properties, idTokenSigner).issue(
        new OidcIdTokenRequest(subject, clientId, nonce, authenticationTime,
                scopes, idTokenClaims, accessToken));

var userInfo = new OidcUserInfoService(properties, userInfoProvider)
        .load(subject, scopes);
```

`oauth-oidc` 不会自动决定用户 Claims 的来源，也不会默认把用户对象全部序列化到 UserInfo，避免发生越权泄露。
