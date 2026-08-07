package cn.richie696.component.oauth.oidc;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** OIDC Backchannel Logout 编排服务，不直接绑定 HTTP 框架或用户会话存储。 */
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
