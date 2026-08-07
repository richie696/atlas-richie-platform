package cn.richie696.component.oauth.oidc;

import java.net.URI;
import java.util.List;

/**
 * 客户端在 OIDC 注销协议里登记的端点配置，承载 front-channel / back-channel 注销 URI 与
 * post-logout redirect URI。
 *
 * <p>处于客户端元数据与 {@link OidcFrontchannelLogoutService} / {@link OidcBackchannelLogoutService}
 * 之间：上游由 OAuth Service 从客户端注册表或 DCR 流程装配，下游被两个注销服务遍历以决定
 * "对哪些 RP 触发哪种通道注销"。它只声明端点是否存在，不参与协议执行。
 *
 * <p>解决"OIDC 注销端点散落在不同客户端配置字段里、编排时容易漏掉"的一致性问题，
 * 让 logout 编排服务能用一个统一的 record 拿到所有所需 URI，同时强制校验 clientId
 * 非空以避免脏数据进入注销流水线。
 *
 * @author richie696
 * @since 2026-08-07
 */
public record OidcClientLogoutConfiguration(
        String clientId,
        URI frontchannelLogoutUri,
        URI backchannelLogoutUri,
        List<String> postLogoutRedirectUris) {

    public OidcClientLogoutConfiguration {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
        postLogoutRedirectUris = postLogoutRedirectUris == null
                ? List.of() : List.copyOf(postLogoutRedirectUris);
    }
}
