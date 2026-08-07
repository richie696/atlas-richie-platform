package cn.richie696.component.oauth.oidc;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 生成 Front-Channel Logout iframe 地址的协议层服务，不接触 HTML 渲染与会话清理。
 *
 * <p>处于 OAuth Service 的注销页面与 {@link OidcClientLogoutConfiguration} 之间：上游接收用户
 * 登出请求与已注册的 RP 客户端列表，下游产出每个 RP 所需的 iframe URL（携带
 * {@code iss} 与可选 {@code sid}）。最终把这些 URL 嵌入 HTML 是 OAuth Service 的工作，
 * 组件本身不输出任何模板字符串。
 *
 * <p>解决"每个 RP 注销 URL 的参数拼接散落在前端或多个 Controller 里"导致的 RP 端点漏注册、
 * iss/sid 编码不一致问题，把 OpenID Connect Front-Channel Logout 的 URL 构造规则收敛成
 * 一个无状态函数，便于单测与跨 AS 复用。
 *
 * @author richie696
 * @since 2026-08-07
 */
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
