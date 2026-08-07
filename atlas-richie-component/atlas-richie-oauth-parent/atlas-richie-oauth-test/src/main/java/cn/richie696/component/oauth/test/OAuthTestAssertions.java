package cn.richie696.component.oauth.test;

import cn.richie696.component.oauth.contract.model.OAuthIntrospectionResponse;
import cn.richie696.component.oauth.contract.model.OAuthTokenResponse;
import cn.richie696.component.oauth.core.model.TokenResponse;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/** 不绑定 JUnit/AssertJ 的 OAuth 测试断言，失败时抛出标准 AssertionError。 */
public final class OAuthTestAssertions {

    private OAuthTestAssertions() {
    }

    public static void assertValidToken(OAuthTokenResponse response) {
        require(response != null, "token response 不能为空");
        requireText(response.accessToken(), "access_token");
        requireText(response.tokenType(), "token_type");
        require(response.expiresIn() > 0, "expires_in 必须大于 0");
    }

    public static void assertValidToken(TokenResponse response) {
        require(response != null, "token response 不能为空");
        requireText(response.getAccessToken(), "access_token");
        requireText(response.getTokenType(), "token_type");
        require(response.getExpiresIn() > 0, "expires_in 必须大于 0");
    }

    public static void assertInactive(OAuthIntrospectionResponse response) {
        require(response != null, "introspection response 不能为空");
        require(!response.active(), "Token 应当是 inactive");
    }

    public static void assertActive(OAuthIntrospectionResponse response, String clientId) {
        require(response != null, "introspection response 不能为空");
        require(response.active(), "Token 应当是 active");
        if (clientId != null) {
            require(clientId.equals(response.clientId()), "introspection client_id 不匹配");
        }
    }

    public static void assertAuthorizationSuccessRedirect(URI redirect, String state) {
        require(redirect != null, "授权成功重定向地址不能为空");
        Map<String, String> parameters = OAuthTestHttp.queryParameters(redirect);
        requireText(parameters.get("code"), "授权码");
        if (state != null) {
            require(state.equals(parameters.get("state")), "OAuth state 不匹配");
        }
        require(parameters.get("error") == null, "成功回调不应包含 error");
    }

    public static void assertOAuthError(Map<String, ?> response, String error) {
        require(response != null, "OAuth 错误响应不能为空");
        require(error != null && error.equals(Objects.toString(response.get("error"), null)),
                "OAuth error 不匹配");
    }

    private static void requireText(String value, String name) {
        require(value != null && !value.isBlank(), name + " 不能为空");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
