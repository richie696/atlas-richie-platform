package cn.richie696.component.oauth.contract.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Token endpoint 的标准请求 record, 覆盖 authorization_code、client_credentials、refresh_token、device_code 四种 grant 在 token 端点所需的最小字段集。
 * <p>
 * 处于契约层 token 端点入参一环, HTTP 适配层把 form / json 反序列化后交给 token endpoint 服务, 上游授权服务按 grant_type 分发到对应处理器, 不再随 grant 类型变化而改动端点签名。
 * 解决"多种 grant 共用一个端点, 但每个 grant 只关心请求里的部分字段、签名随 grant 增长反复变更"的问题, 用一份统一 record 把所有 grant 的入口形状固化下来。
 *
 * @author richie696
 * @since 2026-08-07
 */
public record OAuthTokenRequest(
        @JsonProperty("grant_type")
        String grantType,
        @JsonProperty("client_id")
        String clientId,
        @JsonProperty("client_secret")
        String clientSecret,
        String code,
        @JsonProperty("code_verifier")
        String codeVerifier,
        @JsonProperty("redirect_uri")
        String redirectUri,
        @JsonProperty("refresh_token")
        String refreshToken,
        String scope,
        String resource,
        @JsonProperty("device_code")
        String deviceCode
) {
    public OAuthTokenRequest(String grantType, String clientId, String clientSecret,
                             String code, String codeVerifier, String redirectUri,
                             String refreshToken, String scope, String resource) {
        this(grantType, clientId, clientSecret, code, codeVerifier, redirectUri,
                refreshToken, scope, resource, null);
    }
}
