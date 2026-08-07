package cn.richie696.component.oauth.test;

import java.util.Objects;

/** OAuth Server 黑盒测试使用的默认端点集合；服务若采用不同前缀可自行构造此 record。 */
public record OAuthTestEndpoints(
        String metadata,
        String authorize,
        String token,
        String introspect,
        String revoke,
        String jwks,
        String register
) {

    public OAuthTestEndpoints {
        metadata = normalize(metadata);
        authorize = normalize(authorize);
        token = normalize(token);
        introspect = normalize(introspect);
        revoke = normalize(revoke);
        jwks = normalize(jwks);
        register = normalize(register);
    }

    public static OAuthTestEndpoints defaults() {
        return new OAuthTestEndpoints(
                "/.well-known/oauth-authorization-server",
                "/oauth2/authorize",
                "/oauth2/token",
                "/oauth2/introspect",
                "/oauth2/revoke",
                "/oauth2/jwks",
                "/oauth2/register");
    }

    private static String normalize(String path) {
        String value = Objects.requireNonNull(path, "endpoint path").trim();
        return value.isEmpty() ? "/" : (value.startsWith("/") ? value : "/" + value);
    }
}
