package cn.richie696.component.oauth.oidc;

import java.time.Instant;

/** Backchannel Logout Token 的领域请求。subject 和 sid 至少存在一个。 */
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
