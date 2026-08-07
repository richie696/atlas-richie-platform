package cn.richie696.component.oauth.authz;

import cn.richie696.component.oauth.authz.spi.AuthorizationCodeStore;
import cn.richie696.component.oauth.contract.model.OAuthAuthorizationRequest;
import cn.richie696.component.oauth.core.ClientRegistry;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.contract.exception.BusinessException;
import cn.richie696.component.oauth.contract.OAuth2Constants;

import java.security.SecureRandom;
import java.util.Base64;

/** 与 Servlet、Session 和 UI 无关的授权请求及授权码服务。 */
public class AuthorizationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClientRegistry clientRegistry;
    private final AuthorizationCodeStore authorizationCodeStore;
    private final PKCESupport pkceSupport;

    public AuthorizationService(ClientRegistry clientRegistry,
                                AuthorizationCodeStore authorizationCodeStore,
                                PKCESupport pkceSupport) {
        this.clientRegistry = clientRegistry;
        this.authorizationCodeStore = authorizationCodeStore;
        this.pkceSupport = pkceSupport;
    }

    /** 校验授权请求并返回规范化请求；登录和同意由 OAuth Service 负责。 */
    public OAuthAuthorizationRequest validate(OAuthAuthorizationRequest request) {
        if (request == null || blank(request.clientId()) || blank(request.redirectUri())) {
            throw error(OAuth2Constants.ERROR_INVALID_REQUEST, "client_id 和 redirect_uri 必填");
        }
        try {
            if (java.net.URI.create(request.redirectUri()).getFragment() != null) {
                throw error(OAuth2Constants.ERROR_INVALID_REQUEST, "redirect_uri 不允许包含 fragment");
            }
        } catch (IllegalArgumentException exception) {
            throw error(OAuth2Constants.ERROR_INVALID_REQUEST, "redirect_uri 格式无效");
        }
        if (!"code".equals(request.responseType())) {
            throw error("unsupported_response_type", "仅支持 response_type=code");
        }
        if (blank(request.codeChallenge()) || !"S256".equalsIgnoreCase(request.codeChallengeMethod())) {
            throw error("invalid_request", "授权码模式必须使用 PKCE S256");
        }
        if (!clientRegistry.isClientValid(request.clientId())) {
            throw error(OAuth2Constants.ERROR_INVALID_CLIENT, "客户端不存在或已禁用");
        }
        ClientConfig client = clientRegistry.getClient(request.clientId());
        if (client != null && client.getRedirectUris() != null && !client.getRedirectUris().isEmpty()
                && !client.getRedirectUris().contains(request.redirectUri())) {
            throw error(OAuth2Constants.ERROR_INVALID_REQUEST, "redirect_uri 未注册");
        }
        if (client != null && client.getScopes() != null && !client.getScopes().isEmpty()
                && request.scopes().stream().anyMatch(scope -> !client.getScopes().contains(scope))) {
            throw error(OAuth2Constants.ERROR_INVALID_SCOPE, "请求的 scope 未授权给客户端");
        }
        if (client != null && client.getGrantTypes() != null && !client.getGrantTypes().isEmpty()
                && !client.getGrantTypes().contains("authorization_code")) {
            throw error("unauthorized_client", "客户端未授权 authorization_code 模式");
        }
        return request;
    }

    /** 仅在调用方已经完成用户认证和授权同意后生成授权码。 */
    public String issueCode(OAuthAuthorizationRequest request, String authenticatedUserId) {
        validate(request);
        if (blank(authenticatedUserId)) {
            throw error("access_denied", "缺少已认证用户");
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        authorizationCodeStore.storeAuthorizationCode(code, request.clientId(), request.redirectUri(),
                request.codeChallenge(), request.codeChallengeMethod(), request.scopes(),
                authenticatedUserId, request.resource(), request.nonce(), 600);
        return code;
    }

    public String createCodeVerifier() {
        return pkceSupport.generateCodeVerifier();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private BusinessException error(String code, String message) {
        return new BusinessException(code, message);
    }
}
