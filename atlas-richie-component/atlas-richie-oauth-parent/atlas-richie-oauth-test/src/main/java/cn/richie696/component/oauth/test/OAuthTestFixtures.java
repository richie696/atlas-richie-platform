package cn.richie696.component.oauth.test;

import cn.richie696.component.oauth.authz.PKCESupport;
import cn.richie696.component.oauth.cache.InMemoryOAuthCache;
import cn.richie696.component.oauth.cache.OAuthCache;
import cn.richie696.component.oauth.contract.model.OAuthAuthorizationRequest;
import cn.richie696.component.oauth.contract.model.OAuthTokenRequest;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.contract.OAuthGrantTypes;
import cn.richie696.component.oauth.oidc.OidcConstants;

import java.util.Arrays;
import java.util.List;

/**
 * OAuth Server 集成测试的最小、稳定测试数据工厂。
 *
 * <p>这里的对象只描述协议和组件领域数据，不绑定具体的 Controller、数据库或 Web 测试框架，
 * 因此可以同时被 OAuth Service、Gateway 和组件模块复用。</p>
 */
public final class OAuthTestFixtures {

    public static final String DEFAULT_CLIENT_ID = "it-oauth-client";
    public static final String DEFAULT_CLIENT_SECRET = "it-oauth-client-secret";
    public static final String DEFAULT_REDIRECT_URI = "https://client.example.test/callback";
    public static final String DEFAULT_USER_ID = "it-user-001";
    public static final String DEFAULT_IP = "127.0.0.1";

    private OAuthTestFixtures() {
    }

    /** 返回不会访问外部 Redis 的内存缓存。 */
    public static OAuthCache cache() {
        return new InMemoryOAuthCache();
    }

    /** 构造兼容历史用法的 client_credentials 测试客户端。 */
    public static ClientConfig client(String clientId, String secret, String... scopes) {
        return ClientConfig.builder()
                .clientId(clientId)
                .clientSecret(secret)
                .clientName("test-client")
                .enabled(true)
                .scopes(scopes == null ? List.of() : List.of(scopes))
                .grantTypes(List.of(OAuthGrantTypes.CLIENT_CREDENTIALS))
                .tokenEndpointAuthMethod("client_secret_post")
                .tokenValidDuration(1)
                .refreshTokenValidDuration(24)
                .build();
    }

    /** 构造授权码 + PKCE 流程使用的机密客户端。 */
    public static ClientConfig authorizationCodeClient(String clientId, String secret,
                                                        String redirectUri, String... scopes) {
        return ClientConfig.builder()
                .clientId(clientId)
                .clientSecret(secret)
                .clientName("authorization-code-test-client")
                .enabled(true)
                .scopes(scopes == null ? List.of() : List.of(scopes))
                .redirectUris(List.of(redirectUri == null ? DEFAULT_REDIRECT_URI : redirectUri))
                .grantTypes(List.of(OAuthGrantTypes.AUTHORIZATION_CODE, OAuthGrantTypes.REFRESH_TOKEN))
                .tokenEndpointAuthMethod("client_secret_post")
                .tokenValidDuration(1)
                .refreshTokenValidDuration(24)
                .build();
    }

    public static ClientConfig defaultClient(String... scopes) {
        return client(DEFAULT_CLIENT_ID, DEFAULT_CLIENT_SECRET, scopes);
    }

    /** 构造带 PKCE S256 参数的授权请求，并同时返回原始 verifier。 */
    public static AuthorizationCodeRequest authorizationCodeRequest(
            String clientId, String redirectUri, String... scopes) {
        PKCESupport pkce = new PKCESupport();
        String verifier = pkce.generateCodeVerifier();
        OAuthAuthorizationRequest request = new OAuthAuthorizationRequest(
                clientId,
                redirectUri == null ? DEFAULT_REDIRECT_URI : redirectUri,
                "code",
                scopes == null ? List.of() : Arrays.stream(scopes).toList(),
                "it-state-001",
                null,
                pkce.generateCodeChallenge(verifier),
                "S256");
        return new AuthorizationCodeRequest(request, verifier);
    }

    public static AuthorizationCodeRequest defaultAuthorizationCodeRequest(String... scopes) {
        return authorizationCodeRequest(DEFAULT_CLIENT_ID, DEFAULT_REDIRECT_URI, scopes);
    }

    /** 构造默认带 openid、nonce 的 OIDC Authorization Code + PKCE 请求。 */
    public static AuthorizationCodeRequest oidcAuthorizationCodeRequest(String clientId,
                                                                         String redirectUri,
                                                                         String... scopes) {
        PKCESupport pkce = new PKCESupport();
        String verifier = pkce.generateCodeVerifier();
        List<String> requestedScopes = new java.util.ArrayList<>();
        requestedScopes.add(OidcConstants.OPENID_SCOPE);
        if (scopes != null) {
            requestedScopes.addAll(Arrays.stream(scopes)
                    .filter(scope -> !OidcConstants.OPENID_SCOPE.equals(scope)).toList());
        }
        OAuthAuthorizationRequest request = new OAuthAuthorizationRequest(
                clientId,
                redirectUri == null ? DEFAULT_REDIRECT_URI : redirectUri,
                "code",
                requestedScopes,
                "it-oidc-state-001",
                null,
                pkce.generateCodeChallenge(verifier),
                "S256",
                "it-oidc-nonce-001");
        return new AuthorizationCodeRequest(request, verifier);
    }

    public static OAuthTokenRequest clientCredentialsTokenRequest(String clientId, String secret,
                                                                  String scope, String resource) {
        return new OAuthTokenRequest(OAuthGrantTypes.CLIENT_CREDENTIALS, clientId, secret,
                null, null, null, null, scope, resource);
    }

    public static OAuthTokenRequest authorizationCodeTokenRequest(String clientId, String secret,
                                                                  AuthorizationCodeRequest authorization,
                                                                  String code) {
        OAuthAuthorizationRequest request = authorization.request();
        return new OAuthTokenRequest(OAuthGrantTypes.AUTHORIZATION_CODE, clientId, secret,
                code, authorization.codeVerifier(), request.redirectUri(), null, null,
                request.resource());
    }

    public static OAuthTokenRequest refreshTokenRequest(String clientId, String secret,
                                                        String refreshToken, String scope) {
        return new OAuthTokenRequest(OAuthGrantTypes.REFRESH_TOKEN, clientId, secret,
                null, null, null, refreshToken, scope, null);
    }

    public record AuthorizationCodeRequest(OAuthAuthorizationRequest request, String codeVerifier) {
    }
}
