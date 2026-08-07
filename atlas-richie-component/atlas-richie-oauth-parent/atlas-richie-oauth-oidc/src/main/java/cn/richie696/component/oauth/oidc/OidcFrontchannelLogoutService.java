package cn.richie696.component.oauth.oidc;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** 生成 Front-Channel Logout iframe 地址；页面渲染由 OAuth Service 负责。 */
public final class OidcFrontchannelLogoutService {

    public List<OidcFrontchannelLogoutFrame> frames(
            OidcBackchannelLogoutService.OidcBackchannelLogoutRequest request,
            Collection<OidcClientLogoutConfiguration> clients,
            String issuer) {
        if (request == null || issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("request and issuer must be present");
        }
        List<OidcFrontchannelLogoutFrame> result = new ArrayList<>();
        if (clients == null) {
            return result;
        }
        for (OidcClientLogoutConfiguration client : clients) {
            URI endpoint = client == null ? null : client.frontchannelLogoutUri();
            if (endpoint == null) {
                continue;
            }
            requireHttpEndpoint(endpoint);
            StringBuilder query = new StringBuilder("iss=")
                    .append(encode(issuer));
            if (request.sessionId() != null && !request.sessionId().isBlank()) {
                query.append("&sid=").append(encode(request.sessionId()));
            }
            result.add(new OidcFrontchannelLogoutFrame(client.clientId(), appendQuery(endpoint, query.toString())));
        }
        return List.copyOf(result);
    }

    private URI appendQuery(URI endpoint, String query) {
        String separator = endpoint.getQuery() == null ? "?" : "&";
        return URI.create(endpoint + separator + query);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void requireHttpEndpoint(URI endpoint) {
        String scheme = endpoint.getScheme();
        if (!endpoint.isAbsolute() || !("https".equalsIgnoreCase(scheme)
                || "http".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("logout endpoint must be an absolute HTTP(S) URI");
        }
    }

    public record OidcFrontchannelLogoutFrame(String clientId, URI iframeUri) {
    }
}
