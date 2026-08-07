package cn.richie696.component.oauth.contract.model;

import java.util.List;

/**
 * RFC 8628 Device Authorization Grant 的请求 record, 仅承载 clientId、scopes、resource 三个最小必要字段。
 * <p>
 * 处于契约层设备授权端点的入参一环, 与 {@link OAuthAuthorizationRequest} 同属 grant_type 入口, 但刻意收敛字段集合, 让无浏览器设备发起的 device flow 走专属模型。
 * 解决"设备场景下复用 authorization_code 那种复杂 request 反而引入大量噪声字段"的问题, 用最小入参保持 device flow 与浏览器授权两条线的边界清晰。
 *
 * @author richie696
 * @since 2026-08-07
 */
public record OAuthDeviceAuthorizationRequest(
        String clientId,
        List<String> scopes,
        String resource
) {
    public OAuthDeviceAuthorizationRequest {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }
}
