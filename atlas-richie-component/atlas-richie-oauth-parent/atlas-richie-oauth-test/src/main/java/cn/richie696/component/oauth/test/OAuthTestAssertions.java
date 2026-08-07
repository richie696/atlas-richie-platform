package cn.richie696.component.oauth.test;

import cn.richie696.component.oauth.contract.model.OAuthIntrospectionResponse;
import cn.richie696.component.oauth.contract.model.OAuthTokenResponse;
import cn.richie696.component.oauth.core.model.TokenResponse;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/**
 * OAuth 测试支撑工具（不属于生产运行时）：协议层断言工具集。
 *
 * <p>职责链位置：在 OAuth 各模块的单元/集成测试里位于"协议契约验证"环节，
 * 上游是 OAuthTestFixtures 等夹具构造的 {@code OAuthTokenResponse} /
 * {@code OAuthIntrospectionResponse} / 授权回调 URI，下游是 JUnit / TestNG 测试类。
 * 它只依赖 JDK 标准的 {@link AssertionError}，刻意不绑定 JUnit/AssertJ，
 * 让协议层的契约断言既能被 JUnit 5 跑，也能被服务工程的其它测试框架跑。</p>
 *
 * <p>解决以下问题：协议层需要在不依赖业务测试栈的前提下校验 token、introspection、
 * 授权回调与 OAuth 错误响应的合法性；该工具集以一组静态断言方法提供"输入合法性 + 关键字段非空"的最小校验，
 * 失败时直接抛出标准 AssertionError，使 IDE 与 CI 都能识别失败位置。</p>
 *
 * @author richie696
 * @since 2026-08-07
 */
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
