package cn.richie696.component.oauth.oidc;

import java.time.Instant;

/**
 * OIDC Backchannel Logout Token 的领域请求，描述"要签发给哪个 RP、绑定哪个用户/会话"。
 *
 * <p>处于 {@link OidcBackchannelLogoutService} 与 {@link OidcLogoutTokenSigner} 之间：
 * 上游编排服务根据登出事件填充 clientId、subject、sessionId、issuedAt，下游被 signer
 * 按协议写入 iss / aud / iat / jti / events / sub / sid 等 Claims。规范要求 subject 与
 * sid 至少存在一个，以便 RP 能在不知道 sub 的情况下凭 sid 完成会话清理。
 *
 * <p>解决"注销 Token 字段顺序与命名在多处硬编码"导致的协议实现漂移问题，把构造 Logout
 * Token 所需的最小输入集中到一个不可变 record，并在 compact constructor 阶段就拦截
 * 必填字段缺失，避免把无效请求传到 signer 后再炸出 NPE。
 *
 * @author richie696
 * @since 2026-08-07
 */
public record OidcLogoutTokenRequest(
        String clientId,
        String subject,
        String sessionId,
        Instant issuedAt) {

    public OidcLogoutTokenRequest {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
        if ((subject == null || subject.isBlank()) && (sessionId == null || sessionId.isBlank())) {
            throw new IllegalArgumentException("subject or sessionId must be present");
        }
        issuedAt = issuedAt == null ? Instant.now() : issuedAt;
    }
}
