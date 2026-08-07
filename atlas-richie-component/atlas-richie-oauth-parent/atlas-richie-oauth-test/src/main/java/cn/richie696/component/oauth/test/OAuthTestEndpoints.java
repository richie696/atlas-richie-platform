package cn.richie696.component.oauth.test;

import java.util.Objects;

/**
 * OAuth 测试支撑工具（不属于生产运行时）：OAuth Server 黑盒测试使用的端点路径集合。
 *
 * <p>职责链位置：处于 {@link OAuthTestHttpClient} 等 HTTP 测试客户端与具体 OAuth Service 之间。
 * 它把 metadata、authorize、token、introspect、revoke、jwks、register 等标准端点路径聚合为不可变 record，
 * 并提供 {@link #defaults()} 与规范化处理（始终以 "/" 开头、空路径兜底为 "/"），
 * 让采用不同路径前缀的服务都能以同一份契约构造测试客户端。</p>
 *
 * <p>解决以下问题：黑盒测试需要稳定可读的端点常量，但又不希望硬编码到具体服务工程的路径；
 * 通过 record 的紧凑构造语法让服务工程能在自己的测试基类里"覆盖默认值"，
 * 而所有 OAuth 协议相关测试用例继续消费同一份路径常量。</p>
 *
 * @author richie696
 * @since 2026-08-07
 */
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
