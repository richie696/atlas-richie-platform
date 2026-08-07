package cn.richie696.component.oauth.oidc;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * OIDC Backchannel Logout 的编排服务，把"用户登出"事件拆解为给每个 RP 投递一份 Logout Token。
 *
 * <p>处于 OAuth Service 与 {@link OidcBackchannelLogoutNotifier} 之间：上游接收来自 OAuth Service
 * 的登出请求（携带 subject 或 sessionId），下游依赖 {@link OidcLogoutTokenSigner} 签名并通过
 * 注入的 notifier 完成 HTTP 投递。组件不直接耦合 HTTP 框架、用户会话存储或客户端元数据源，
 * 业务侧在装配阶段一并把这些依赖补齐。
 *
 * <p>解决"OP 想让多个 RP 同步登出却不得不自己遍历客户端 + 签名 + HTTP 投递"导致的协议逻辑散落
 * 问题，把 OpenID Connect Backchannel Logout 的协议面收敛在一处，让 AS 只需关心"用户下线了"
 * 这一个事件，剩下的会话清理与会话失效由各 RP 自己完成。
 *
 * @author richie696
 * @since 2026-08-07
 */
public final class OidcBackchannelLogoutService {

    public static final String BACKCHANNEL_LOGOUT_EVENT =
            "http://schemas.openid.net/event/backchannel-logout";

    private final OidcLogoutTokenSigner signer;

    public OidcBackchannelLogoutService(OidcLogoutTokenSigner signer) {
        this.signer = signer;
    }

    public List<OidcLogoutDelivery> prepare(OidcBackchannelLogoutRequest request,
                                            Collection<OidcClientLogoutConfiguration> clients) {
        if (request == null) {
            throw new IllegalArgumentException("logout request must not be null");
        }
        if (signer == null) {
            throw new IllegalStateException("logout token signer must be configured");
        }
        List<OidcLogoutDelivery> result = new ArrayList<>();
        if (clients == null) {
            return result;
        }
        for (OidcClientLogoutConfiguration client : clients) {
            URI endpoint = client == null ? null : client.backchannelLogoutUri();
            if (endpoint == null) {
                continue;
            }
            requireHttpEndpoint(endpoint);
            String token = signer.sign(new OidcLogoutTokenRequest(
                    client.clientId(), request.subject(), request.sessionId(), request.issuedAt()));
            result.add(new OidcLogoutDelivery(client.clientId(), endpoint, token));
        }
        return List.copyOf(result);
    }

    public List<OidcLogoutDelivery> logout(OidcBackchannelLogoutRequest request,
                                           Collection<OidcClientLogoutConfiguration> clients,
                                           OidcBackchannelLogoutNotifier notifier) {
        if (notifier == null) {
            throw new IllegalArgumentException("notifier must not be null");
        }
        List<OidcLogoutDelivery> deliveries = prepare(request, clients);
        deliveries.forEach(delivery -> notifier.notify(delivery.endpoint(), delivery.logoutToken()));
        return deliveries;
    }

    private void requireHttpEndpoint(URI endpoint) {
        String scheme = endpoint.getScheme();
        if (!endpoint.isAbsolute() || !("https".equalsIgnoreCase(scheme)
                || "http".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("logout endpoint must be an absolute HTTP(S) URI");
        }
    }

    public record OidcBackchannelLogoutRequest(String subject, String sessionId, Instant issuedAt) {
        public OidcBackchannelLogoutRequest {
            if ((subject == null || subject.isBlank()) && (sessionId == null || sessionId.isBlank())) {
                throw new IllegalArgumentException("subject or sessionId must be present");
            }
            issuedAt = issuedAt == null ? Instant.now() : issuedAt;
        }
    }

    public record OidcLogoutDelivery(String clientId, URI endpoint, String logoutToken) {
    }
}
