package cn.richie696.component.mcp.security.oauth;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * 不暴露给业务的 OAuth access token 快照。
 *
 * <p>作为 MCP 体系内令牌对象的最小内部表示，{@code McpOAuthAccessToken} 屏蔽了上游 OAuth provider
 * 的字段差异（不同 AS 在 token 响应中可能省略 issuer / resource 等 claim），同时把 RFC 6750 要求的
 * {@code Bearer} tokenType 提供合理默认值。该 record 不实现序列化，也不会被持久化——它仅在
 * 进程内传递，敏感字段（{@code value}）应避免出现在日志或链路追踪中。</p>
 *
 * <p>为什么是 record 而非 class：record 自动提供基于字段的 equals/hashCode/toString，使
 * {@link McpOAuthTokenManager} 中的 {@code AtomicReference<McpOAuthAccessToken>} 缓存比对与
 * 替换语义自然成立；不可变结构也避免了令牌被多处代码意外篡改。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public record McpOAuthAccessToken(
        String value,
        String tokenType,
        Instant expiresAt,
        String issuer,
        String resource,
        Set<String> scopes) {

    /**
     * 紧凑构造器：对 value/tokenType/scopes 做必填校验与归一化。
     *
     * <p>tokenType 默认值选择 {@code Bearer}：RFC 6750 是 MCP transport 唯一强制支持的认证方案，
     * 当上游 AS 省略 token_type 时回退到 Bearer 比抛错更符合"鲁棒性原则"，因为大多数 AS 即使
     * 不返回也按 Bearer 语义处理请求。</p>
     *
     * @param value 令牌原文（必填，非空）
     * @param tokenType 令牌类型，null/blank 时默认为 {@code Bearer}
     * @param expiresAt 过期时间，可为 null（视为永不过期）
     * @param issuer 颁发方标识，可为 null
     * @param resource 受众/资源指示符（RFC 8707），可为 null
     * @param scopes 授权范围集合，null 视为空集
     * @throws IllegalArgumentException 当 value 为 null 或 blank 时
     */
    public McpOAuthAccessToken {
        value = required(value, "value");
        tokenType = tokenType == null || tokenType.isBlank() ? "Bearer" : tokenType;
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }

    /**
     * 判断令牌在指定的时钟偏差容忍下是否已过期。
     *
     * <p>当 {@code expiresAt} 为 null 时，按"永不过期"处理：部分自包含 JWT 或 opaque token 场景下
     * 不携带 exp 字段，依赖 AS 主动失效；此时调用方应自行通过 introspection 校验活性。
     * 时钟偏差（{@code clockSkew}）的引入是为了规避客户端与 AS 之间的小幅 NTP 漂移，避免
     * "刚换新令牌立刻被判过期"的边缘情况。</p>
     *
     * @param clockSkew 容忍的时钟偏差；null 等价于 {@link Duration#ZERO}
     * @return {@code true} 表示已过期或在容忍窗口内即将过期，{@code false} 表示仍可用
     */
    public boolean expired(Duration clockSkew) {
        if (expiresAt == null) {
            return false;
        }
        Duration skew = clockSkew == null ? Duration.ZERO : clockSkew;
        return !expiresAt.isAfter(Instant.now().plus(skew));
    }

    /**
     * 拼接符合 RFC 6750 的 {@code Authorization} 头值（{@code <type> <value>}）。
     *
     * <p>为什么不直接用 {@link String#format}：该方法会被频繁调用（MCP 每个请求都要注入 token），
     * 简单字符串拼接在 JIT 内联后比格式化器更廉价，且格式固定无国际化需求。</p>
     *
     * @return 形如 {@code "Bearer eyJhbGc..."} 的头值
     */
    public String authorizationHeader() {
        return tokenType + " " + value;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
